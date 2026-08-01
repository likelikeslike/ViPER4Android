#!/usr/bin/env python3
"""Convert ViPER4Android v1 (flat JSON) and legacy XML presets to v2 grouped JSON.

Two input formats are accepted:

- v1 JSON: the app's own pre-schema-2 preset, flat keys like ``masterEnabled``,
  ``fetThreshold``, ``eqBands`` (";"-joined arrays).
- legacy XML: the old ViPER4Android ``<map>`` preset (parameter ids like
  ``36868``), including layouts older than v2.7.2.x. These are first translated
  to the v1 flat form, then to v2.

Output is the v2 grouped JSON (``schemaVersion: 2``). Fields absent from the
input are filled with the app's defaults (from ``EffectStates.kt`` /
``EffectPrefs.kt`` @ commit 064684c3).

Input format (v1 JSON vs XML) and, for v1 JSON, the headphone/speaker namespace
are both auto-detected; no flags are needed for the common case.

Examples::

    # v1 JSON preset -> v2
    convert_preset.py hp.json -o hp.v2.json
    convert_preset.py spk.json -o spk.v2.json

    # legacy XML preset -> v2
    convert_preset.py preset.xml -o default_m1.v2.json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

GROUP_ORDER: dict[str, list[str]] = {
    "masterLimiter": ["threshold", "outputVolume", "channelPan"],
    "playbackGainControl": ["enable", "strength", "maxGain", "outputThreshold"],
    "lufs": ["enable", "target", "maxGain", "speed"],
    "fetCompressor": [
        "enable",
        "threshold",
        "ratio",
        "kneeAuto",
        "knee",
        "kneeMulti",
        "gainAuto",
        "gain",
        "attackAuto",
        "attack",
        "maxAttack",
        "releaseAuto",
        "release",
        "maxRelease",
        "crest",
        "adapt",
        "noClip",
    ],
    "multibandCompressor": [
        "enable",
        "bandEnables",
        "crossovers",
        "thresholds",
        "ratios",
        "gains",
        "knees",
        "kneeMultis",
        "attacks",
        "maxAttacks",
        "releases",
        "maxReleases",
        "crests",
        "adapts",
        "kneeAutos",
        "gainAutos",
        "attackAutos",
        "releaseAutos",
        "noClips",
    ],
    "ddc": ["enable", "device"],
    "spectrumExtension": ["enable", "strength", "exciter"],
    "equalizer": ["enable", "bandCount", "bands", "presetId"],
    "dynamicEq": [
        "enable",
        "bandCount",
        "freqs",
        "qs",
        "gains",
        "thresholds",
        "attacks",
        "releases",
        "filterTypes",
    ],
    "convolver": ["enable", "kernelFile", "crossChannel"],
    "fieldSurround": ["enable", "widening", "midImage", "depth"],
    "diffSurround": ["enable", "delay", "reverse", "wetDryMix", "lpCutoff"],
    "stereoImager": [
        "enable",
        "lowWidth",
        "midWidth",
        "highWidth",
        "lowCrossover",
        "highCrossover",
    ],
    "headphoneSurround": ["enable", "quality"],
    "reverb": ["enable", "roomSize", "width", "damp", "wet", "dry"],
    "dynamicSystem": [
        "enable",
        "presetId",
        "device",
        "strength",
        "xLow",
        "xHigh",
        "yLow",
        "yHigh",
        "sideGainLow",
        "sideGainHigh",
    ],
    "psychoacousticBass": [
        "enable",
        "cutoff",
        "intensity",
        "harmonicOrder",
        "originalLevel",
    ],
    "bass": ["enable", "mode", "frequency", "gain", "antiPop"],
    "bassMono": ["enable", "mode", "frequency", "gain", "antiPop"],
    "clarity": ["enable", "mode", "gain"],
    "cure": ["enable", "crossfeedPreset"],
    "tubeSimulator": ["enable"],
    "analogX": ["enable", "mode"],
    "speakerCorrection": ["enable"],
}

PREF_TABLE: dict[str, dict[str, Any]] = {
    "outputVolume": {
        "type": "IntPref",
        "spk": "spkOutputVolume",
        "default": 100,
        "slot": ("group", "masterLimiter", "outputVolume"),
        "range": (1, 200),
    },
    "channelPan": {
        "type": "IntPref",
        "spk": "spkChannelPan",
        "default": 0,
        "slot": ("group", "masterLimiter", "channelPan"),
        "range": (-100, 100),
    },
    "limiter": {
        "type": "IntPref",
        "spk": "spkLimiter",
        "default": 100,
        "slot": ("group", "masterLimiter", "threshold"),
        "range": (30, 100),
    },
    "agcEnabled": {
        "type": "BoolPref",
        "spk": "spkAgcEnabled",
        "default": False,
        "slot": ("group", "playbackGainControl", "enable"),
    },
    "agcStrength": {
        "type": "IntPref",
        "spk": "spkAgcStrength",
        "default": 100,
        "slot": ("group", "playbackGainControl", "strength"),
        "range": (50, 300),
    },
    "agcMaxGain": {
        "type": "IntPref",
        "spk": "spkAgcMaxGain",
        "default": 100,
        "slot": ("group", "playbackGainControl", "maxGain"),
        "range": (100, 1000),
    },
    "agcOutputThreshold": {
        "type": "IntPref",
        "spk": "spkAgcOutputThreshold",
        "default": 100,
        "slot": ("group", "playbackGainControl", "outputThreshold"),
        "range": (30, 100),
    },
    "lufsEnabled": {
        "type": "BoolPref",
        "spk": "spkLufsEnabled",
        "default": False,
        "slot": ("group", "lufs", "enable"),
    },
    "lufsTarget": {
        "type": "IntPref",
        "spk": "spkLufsTarget",
        "default": 140,
        "slot": ("group", "lufs", "target"),
        "range": (80, 240),
    },
    "lufsMaxGain": {
        "type": "IntPref",
        "spk": "spkLufsMaxGain",
        "default": 60,
        "slot": ("group", "lufs", "maxGain"),
        "range": (0, 120),
    },
    "lufsSpeed": {
        "type": "IntPref",
        "spk": "spkLufsSpeed",
        "default": 1,
        "slot": ("group", "lufs", "speed"),
        "range": (0, 2),
    },
    "fetEnabled": {
        "type": "BoolPref",
        "spk": "spkFetEnabled",
        "default": False,
        "slot": ("group", "fetCompressor", "enable"),
    },
    "fetThreshold": {
        "type": "IntPref",
        "spk": "spkFetThreshold",
        "default": 100,
        "slot": ("group", "fetCompressor", "threshold"),
        "range": (-48, 0),
    },
    "fetRatio": {
        "type": "IntPref",
        "spk": "spkFetRatio",
        "default": 100,
        "slot": ("group", "fetCompressor", "ratio"),
        "range": (0, 200),
    },
    "fetAutoKnee": {
        "type": "BoolPref",
        "spk": "spkFetAutoKnee",
        "default": True,
        "slot": ("group", "fetCompressor", "kneeAuto"),
    },
    "fetKnee": {
        "type": "IntPref",
        "spk": "spkFetKnee",
        "default": 0,
        "slot": ("group", "fetCompressor", "knee"),
        "range": (0, 12),
    },
    "fetKneeMulti": {
        "type": "IntPref",
        "spk": "spkFetKneeMulti",
        "default": 0,
        "slot": ("group", "fetCompressor", "kneeMulti"),
        "range": (0, 100),
    },
    "fetAutoGain": {
        "type": "BoolPref",
        "spk": "spkFetAutoGain",
        "default": True,
        "slot": ("group", "fetCompressor", "gainAuto"),
    },
    "fetGain": {
        "type": "IntPref",
        "spk": "spkFetGain",
        "default": 0,
        "slot": ("group", "fetCompressor", "gain"),
        "range": (0, 24),
    },
    "fetAutoAttack": {
        "type": "BoolPref",
        "spk": "spkFetAutoAttack",
        "default": True,
        "slot": ("group", "fetCompressor", "attackAuto"),
    },
    "fetAttack": {
        "type": "IntPref",
        "spk": "spkFetAttack",
        "default": 20,
        "slot": ("group", "fetCompressor", "attack"),
        "range": (1, 100),
    },
    "fetMaxAttack": {
        "type": "IntPref",
        "spk": "spkFetMaxAttack",
        "default": 80,
        "slot": ("group", "fetCompressor", "maxAttack"),
        "range": (1, 100),
    },
    "fetAutoRelease": {
        "type": "BoolPref",
        "spk": "spkFetAutoRelease",
        "default": True,
        "slot": ("group", "fetCompressor", "releaseAuto"),
    },
    "fetRelease": {
        "type": "IntPref",
        "spk": "spkFetRelease",
        "default": 50,
        "slot": ("group", "fetCompressor", "release"),
        "range": (5, 500),
    },
    "fetMaxRelease": {
        "type": "IntPref",
        "spk": "spkFetMaxRelease",
        "default": 100,
        "slot": ("group", "fetCompressor", "maxRelease"),
        "range": (5, 500),
    },
    "fetCrest": {
        "type": "IntPref",
        "spk": "spkFetCrest",
        "default": 100,
        "slot": ("group", "fetCompressor", "crest"),
        "range": (5, 300),
    },
    "fetAdapt": {
        "type": "IntPref",
        "spk": "spkFetAdapt",
        "default": 50,
        "slot": ("group", "fetCompressor", "adapt"),
        "range": (0, 200),
    },
    "fetNoClip": {
        "type": "BoolPref",
        "spk": "spkFetNoClip",
        "default": True,
        "slot": ("group", "fetCompressor", "noClip"),
    },
    "mbcEnabled": {
        "type": "BoolPref",
        "spk": "spkMbcEnabled",
        "default": False,
        "slot": ("group", "multibandCompressor", "enable"),
    },
    "mbcBandEnables": {
        "type": "StringPref",
        "spk": "spkMbcBandEnables",
        "default": "1;1;1;1;1",
        "slot": ("array", "multibandCompressor", "bandEnables", "BOOL01"),
    },
    "mbcCrossovers": {
        "type": "StringPref",
        "spk": "spkMbcCrossovers",
        "default": "120;500;4000;8000",
        "slot": ("array", "multibandCompressor", "crossovers", "INT"),
        "range": (30, 16000),
    },
    "mbcThresholds": {
        "type": "StringPref",
        "spk": "spkMbcThresholds",
        "default": "-18;-18;-18;-18;-18",
        "slot": ("array", "multibandCompressor", "thresholds", "INT"),
        "range": (-48, 0),
    },
    "mbcRatios": {
        "type": "StringPref",
        "spk": "spkMbcRatios",
        "default": "50;50;50;50;50",
        "slot": ("array", "multibandCompressor", "ratios", "INT"),
        "range": (0, 200),
    },
    "mbcGains": {
        "type": "StringPref",
        "spk": "spkMbcGains",
        "default": "24;24;24;24;24",
        "slot": ("array", "multibandCompressor", "gains", "INT"),
        "range": (0, 24),
    },
    "mbcKnees": {
        "type": "StringPref",
        "spk": "spkMbcKnees",
        "default": "0;0;0;0;0",
        "slot": ("array", "multibandCompressor", "knees", "INT"),
        "range": (0, 12),
    },
    "mbcKneeMultis": {
        "type": "StringPref",
        "spk": "spkMbcKneeMultis",
        "default": "0;0;0;0;0",
        "slot": ("array", "multibandCompressor", "kneeMultis", "INT"),
        "range": (0, 100),
    },
    "mbcAttacks": {
        "type": "StringPref",
        "spk": "spkMbcAttacks",
        "default": "1;1;1;1;1",
        "slot": ("array", "multibandCompressor", "attacks", "INT"),
        "range": (1, 100),
    },
    "mbcMaxAttacks": {
        "type": "StringPref",
        "spk": "spkMbcMaxAttacks",
        "default": "44;44;44;44;44",
        "slot": ("array", "multibandCompressor", "maxAttacks", "INT"),
        "range": (1, 100),
    },
    "mbcReleases": {
        "type": "StringPref",
        "spk": "spkMbcReleases",
        "default": "100;100;100;100;100",
        "slot": ("array", "multibandCompressor", "releases", "INT"),
        "range": (5, 500),
    },
    "mbcMaxReleases": {
        "type": "StringPref",
        "spk": "spkMbcMaxReleases",
        "default": "200;200;200;200;200",
        "slot": ("array", "multibandCompressor", "maxReleases", "INT"),
        "range": (5, 500),
    },
    "mbcCrests": {
        "type": "StringPref",
        "spk": "spkMbcCrests",
        "default": "100;100;100;100;100",
        "slot": ("array", "multibandCompressor", "crests", "INT"),
        "range": (5, 300),
    },
    "mbcAdapts": {
        "type": "StringPref",
        "spk": "spkMbcAdapts",
        "default": "50;50;50;50;50",
        "slot": ("array", "multibandCompressor", "adapts", "INT"),
        "range": (0, 200),
    },
    "mbcAutoKnees": {
        "type": "StringPref",
        "spk": "spkMbcAutoKnees",
        "default": "1;1;1;1;1",
        "slot": ("array", "multibandCompressor", "kneeAutos", "BOOL01"),
    },
    "mbcAutoGains": {
        "type": "StringPref",
        "spk": "spkMbcAutoGains",
        "default": "1;1;1;1;1",
        "slot": ("array", "multibandCompressor", "gainAutos", "BOOL01"),
    },
    "mbcAutoAttacks": {
        "type": "StringPref",
        "spk": "spkMbcAutoAttacks",
        "default": "1;1;1;1;1",
        "slot": ("array", "multibandCompressor", "attackAutos", "BOOL01"),
    },
    "mbcAutoReleases": {
        "type": "StringPref",
        "spk": "spkMbcAutoReleases",
        "default": "1;1;1;1;1",
        "slot": ("array", "multibandCompressor", "releaseAutos", "BOOL01"),
    },
    "mbcNoClips": {
        "type": "StringPref",
        "spk": "spkMbcNoClips",
        "default": "1;1;1;1;1",
        "slot": ("array", "multibandCompressor", "noClips", "BOOL01"),
    },
    "ddcEnabled": {
        "type": "BoolPref",
        "spk": "spkDdcEnabled",
        "default": False,
        "slot": ("group", "ddc", "enable"),
    },
    "ddcDevice": {
        "type": "StringPref",
        "spk": "spkDdcDevice",
        "default": "",
        "slot": ("group", "ddc", "device"),
    },
    "vseEnabled": {
        "type": "BoolPref",
        "spk": "spkVseEnabled",
        "default": False,
        "slot": ("group", "spectrumExtension", "enable"),
    },
    "vseStrength": {
        "type": "IntPref",
        "spk": "spkVseStrength",
        "default": 7600,
        "slot": ("group", "spectrumExtension", "strength"),
        "range": (2200, 8200),
    },
    "vseExciter": {
        "type": "IntPref",
        "spk": "spkVseExciter",
        "default": 0,
        "slot": ("group", "spectrumExtension", "exciter"),
        "range": (0, 100),
    },
    "eqEnabled": {
        "type": "BoolPref",
        "spk": "spkEqEnabled",
        "default": False,
        "slot": ("group", "equalizer", "enable"),
    },
    "eqBandCount": {
        "type": "IntPref",
        "spk": "spkEqBandCount",
        "default": 10,
        "slot": ("group", "equalizer", "bandCount"),
    },
    "eqBands": {
        "type": "StringPref",
        "spk": "spkEqBands",
        "default": "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "slot": ("array", "equalizer", "bands", "DOUBLE"),
        "range": (-12.0, 12.0),
    },
    "eqPresetId": {
        "type": "NullableLongPref",
        "spk": "spkEqPresetId",
        "default": None,
        "slot": ("group", "equalizer", "presetId"),
    },
    "dynamicEqEnabled": {
        "type": "BoolPref",
        "spk": "spkDynamicEqEnabled",
        "default": False,
        "slot": ("group", "dynamicEq", "enable"),
    },
    "dynamicEqBandCount": {
        "type": "IntPref",
        "spk": "spkDynamicEqBandCount",
        "default": 3,
        "slot": ("group", "dynamicEq", "bandCount"),
    },
    "dynamicEqFreqs": {
        "type": "StringPref",
        "spk": "spkDynamicEqFreqs",
        "default": "60;150;400",
        "slot": ("array", "dynamicEq", "freqs", "INT"),
        "range": (20, 20000),
    },
    "dynamicEqQs": {
        "type": "StringPref",
        "spk": "spkDynamicEqQs",
        "default": "100;100;150",
        "slot": ("array", "dynamicEq", "qs", "INT"),
        "range": (50, 800),
    },
    "dynamicEqGains": {
        "type": "StringPref",
        "spk": "spkDynamicEqGains",
        "default": "0;0;0",
        "slot": ("array", "dynamicEq", "gains", "INT"),
        "range": (-120, 120),
    },
    "dynamicEqThresholds": {
        "type": "StringPref",
        "spk": "spkDynamicEqThresholds",
        "default": "-200;-200;-200",
        "slot": ("array", "dynamicEq", "thresholds", "INT"),
        "range": (-800, 0),
    },
    "dynamicEqAttacks": {
        "type": "StringPref",
        "spk": "spkDynamicEqAttacks",
        "default": "10;10;10",
        "slot": ("array", "dynamicEq", "attacks", "INT"),
        "range": (1, 100),
    },
    "dynamicEqReleases": {
        "type": "StringPref",
        "spk": "spkDynamicEqReleases",
        "default": "100;100;100",
        "slot": ("array", "dynamicEq", "releases", "INT"),
        "range": (10, 500),
    },
    "dynamicEqFilterTypes": {
        "type": "StringPref",
        "spk": "spkDynamicEqFilterTypes",
        "default": "0;0;0",
        "slot": ("array", "dynamicEq", "filterTypes", "INT"),
    },
    "convolverEnabled": {
        "type": "BoolPref",
        "spk": "spkConvolverEnabled",
        "default": False,
        "slot": ("group", "convolver", "enable"),
    },
    "convolverKernel": {
        "type": "StringPref",
        "spk": "spkConvolverKernel",
        "default": "",
        "slot": ("group", "convolver", "kernelFile"),
    },
    "convolverCrossChannel": {
        "type": "IntPref",
        "spk": "spkConvolverCrossChannel",
        "default": 0,
        "slot": ("group", "convolver", "crossChannel"),
        "range": (0, 100),
    },
    "fieldSurroundEnabled": {
        "type": "BoolPref",
        "spk": "spkFieldSurroundEnabled",
        "default": False,
        "slot": ("group", "fieldSurround", "enable"),
    },
    "fieldSurroundWidening": {
        "type": "IntPref",
        "spk": "spkFieldSurroundWidening",
        "default": 0,
        "slot": ("group", "fieldSurround", "widening"),
        "range": (0, 8),
    },
    "fieldSurroundMidImage": {
        "type": "IntPref",
        "spk": "spkFieldSurroundMidImage",
        "default": 5,
        "slot": ("group", "fieldSurround", "midImage"),
        "range": (0, 10),
    },
    "fieldSurroundDepth": {
        "type": "IntPref",
        "spk": "spkFieldSurroundDepth",
        "default": 0,
        "slot": ("group", "fieldSurround", "depth"),
        "range": (0, 10),
    },
    "diffSurroundEnabled": {
        "type": "BoolPref",
        "spk": "spkDiffSurroundEnabled",
        "default": False,
        "slot": ("group", "diffSurround", "enable"),
    },
    "diffSurroundDelay": {
        "type": "IntPref",
        "spk": "spkDiffSurroundDelay",
        "default": 5,
        "slot": ("group", "diffSurround", "delay"),
        "range": (1, 20),
    },
    "diffSurroundReverse": {
        "type": "BoolPref",
        "spk": "spkDiffSurroundReverse",
        "default": False,
        "slot": ("group", "diffSurround", "reverse"),
    },
    "diffSurroundWetDryMix": {
        "type": "IntPref",
        "spk": "spkDiffSurroundWetDryMix",
        "default": 100,
        "slot": ("group", "diffSurround", "wetDryMix"),
        "range": (0, 100),
    },
    "diffSurroundLpCutoff": {
        "type": "IntPref",
        "spk": "spkDiffSurroundLpCutoff",
        "default": 0,
        "slot": ("group", "diffSurround", "lpCutoff"),
        "range": (0, 20000),
    },
    "stereoImgEnabled": {
        "type": "BoolPref",
        "spk": "spkStereoImgEnabled",
        "default": False,
        "slot": ("group", "stereoImager", "enable"),
    },
    "stereoImgLowWidth": {
        "type": "IntPref",
        "spk": "spkStereoImgLowWidth",
        "default": 100,
        "slot": ("group", "stereoImager", "lowWidth"),
        "range": (0, 200),
    },
    "stereoImgMidWidth": {
        "type": "IntPref",
        "spk": "spkStereoImgMidWidth",
        "default": 100,
        "slot": ("group", "stereoImager", "midWidth"),
        "range": (0, 200),
    },
    "stereoImgHighWidth": {
        "type": "IntPref",
        "spk": "spkStereoImgHighWidth",
        "default": 100,
        "slot": ("group", "stereoImager", "highWidth"),
        "range": (0, 200),
    },
    "stereoImgLowCrossover": {
        "type": "IntPref",
        "spk": "spkStereoImgLowCrossover",
        "default": 200,
        "slot": ("group", "stereoImager", "lowCrossover"),
        "range": (80, 400),
    },
    "stereoImgHighCrossover": {
        "type": "IntPref",
        "spk": "spkStereoImgHighCrossover",
        "default": 4000,
        "slot": ("group", "stereoImager", "highCrossover"),
        "range": (2000, 8000),
    },
    "vheEnabled": {
        "type": "BoolPref",
        "spk": "spkVheEnabled",
        "default": False,
        "slot": ("group", "headphoneSurround", "enable"),
    },
    "vheQuality": {
        "type": "IntPref",
        "spk": "spkVheQuality",
        "default": 0,
        "slot": ("group", "headphoneSurround", "quality"),
        "range": (0, 4),
    },
    "reverbEnabled": {
        "type": "BoolPref",
        "spk": "spkReverbEnabled",
        "default": False,
        "slot": ("group", "reverb", "enable"),
    },
    "reverbRoomSize": {
        "type": "IntPref",
        "spk": "spkReverbRoomSize",
        "default": 0,
        "slot": ("group", "reverb", "roomSize"),
        "range": (0, 10),
    },
    "reverbWidth": {
        "type": "IntPref",
        "spk": "spkReverbWidth",
        "default": 0,
        "slot": ("group", "reverb", "width"),
        "range": (0, 10),
    },
    "reverbDampening": {
        "type": "IntPref",
        "spk": "spkReverbDampening",
        "default": 0,
        "slot": ("group", "reverb", "damp"),
        "range": (0, 10),
    },
    "reverbWet": {
        "type": "IntPref",
        "spk": "spkReverbWet",
        "default": 0,
        "slot": ("group", "reverb", "wet"),
        "range": (0, 100),
    },
    "reverbDry": {
        "type": "IntPref",
        "spk": "spkReverbDry",
        "default": 50,
        "slot": ("group", "reverb", "dry"),
        "range": (0, 100),
    },
    "dynamicSystemEnabled": {
        "type": "BoolPref",
        "spk": "spkDynamicSystemEnabled",
        "default": False,
        "slot": ("group", "dynamicSystem", "enable"),
    },
    "dsPresetId": {
        "type": "NullableLongPref",
        "spk": "spkDsPresetId",
        "default": None,
        "slot": ("group", "dynamicSystem", "presetId"),
    },
    "dynamicSystemDevice": {
        "type": "IntPref",
        "spk": "spkDynamicSystemDevice",
        "default": 0,
        "slot": ("group", "dynamicSystem", "device"),
    },
    "dynamicSystemStrength": {
        "type": "IntPref",
        "spk": "spkDynamicSystemStrength",
        "default": 50,
        "slot": ("group", "dynamicSystem", "strength"),
        "range": (0, 100),
    },
    "dsXLow": {
        "type": "IntPref",
        "spk": "spkDsXLow",
        "default": 100,
        "slot": ("group", "dynamicSystem", "xLow"),
        "range": (0, 2400),
    },
    "dsXHigh": {
        "type": "IntPref",
        "spk": "spkDsXHigh",
        "default": 5600,
        "slot": ("group", "dynamicSystem", "xHigh"),
        "range": (0, 12000),
    },
    "dsYLow": {
        "type": "IntPref",
        "spk": "spkDsYLow",
        "default": 40,
        "slot": ("group", "dynamicSystem", "yLow"),
        "range": (0, 200),
    },
    "dsYHigh": {
        "type": "IntPref",
        "spk": "spkDsYHigh",
        "default": 80,
        "slot": ("group", "dynamicSystem", "yHigh"),
        "range": (0, 300),
    },
    "dsSideGainLow": {
        "type": "IntPref",
        "spk": "spkDsSideGainLow",
        "default": 50,
        "slot": ("group", "dynamicSystem", "sideGainLow"),
        "range": (0, 100),
    },
    "dsSideGainHigh": {
        "type": "IntPref",
        "spk": "spkDsSideGainHigh",
        "default": 50,
        "slot": ("group", "dynamicSystem", "sideGainHigh"),
        "range": (0, 100),
    },
    "tubeSimulatorEnabled": {
        "type": "BoolPref",
        "spk": "spkTubeSimulatorEnabled",
        "default": False,
        "slot": ("group", "tubeSimulator", "enable"),
    },
    "psychoBassEnabled": {
        "type": "BoolPref",
        "spk": "spkPsychoBassEnabled",
        "default": False,
        "slot": ("group", "psychoacousticBass", "enable"),
    },
    "psychoBassCutoff": {
        "type": "IntPref",
        "spk": "spkPsychoBassCutoff",
        "default": 80,
        "slot": ("group", "psychoacousticBass", "cutoff"),
        "range": (60, 150),
    },
    "psychoBassIntensity": {
        "type": "IntPref",
        "spk": "spkPsychoBassIntensity",
        "default": 50,
        "slot": ("group", "psychoacousticBass", "intensity"),
        "range": (0, 100),
    },
    "psychoBassHarmonicOrder": {
        "type": "IntPref",
        "spk": "spkPsychoBassHarmonicOrder",
        "default": 3,
        "slot": ("group", "psychoacousticBass", "harmonicOrder"),
        "range": (2, 5),
    },
    "psychoBassOriginalLevel": {
        "type": "IntPref",
        "spk": "spkPsychoBassOriginalLevel",
        "default": 100,
        "slot": ("group", "psychoacousticBass", "originalLevel"),
        "range": (0, 100),
    },
    "bassEnabled": {
        "type": "BoolPref",
        "spk": "spkBassEnabled",
        "default": False,
        "slot": ("group", "bass", "enable"),
    },
    "bassMode": {
        "type": "IntPref",
        "spk": "spkBassMode",
        "default": 0,
        "slot": ("group", "bass", "mode"),
    },
    "bassFrequency": {
        "type": "IntPref",
        "spk": "spkBassFrequency",
        "default": 55,
        "slot": ("group", "bass", "frequency"),
        "range": (0, 135),
    },
    "bassGain": {
        "type": "IntPref",
        "spk": "spkBassGain",
        "default": 50,
        "slot": ("group", "bass", "gain"),
        "range": (50, 1000),
    },
    "bassAntiPop": {
        "type": "BoolPref",
        "spk": "spkBassAntiPop",
        "default": True,
        "slot": ("group", "bass", "antiPop"),
    },
    "bassMonoEnabled": {
        "type": "BoolPref",
        "spk": "spkBassMonoEnabled",
        "default": False,
        "slot": ("group", "bassMono", "enable"),
    },
    "bassMonoMode": {
        "type": "IntPref",
        "spk": "spkBassMonoMode",
        "default": 0,
        "slot": ("group", "bassMono", "mode"),
    },
    "bassMonoFrequency": {
        "type": "IntPref",
        "spk": "spkBassMonoFrequency",
        "default": 55,
        "slot": ("group", "bassMono", "frequency"),
        "range": (0, 135),
    },
    "bassMonoGain": {
        "type": "IntPref",
        "spk": "spkBassMonoGain",
        "default": 50,
        "slot": ("group", "bassMono", "gain"),
        "range": (50, 1000),
    },
    "bassMonoAntiPop": {
        "type": "BoolPref",
        "spk": "spkBassMonoAntiPop",
        "default": True,
        "slot": ("group", "bassMono", "antiPop"),
    },
    "clarityEnabled": {
        "type": "BoolPref",
        "spk": "spkClarityEnabled",
        "default": False,
        "slot": ("group", "clarity", "enable"),
    },
    "clarityMode": {
        "type": "IntPref",
        "spk": "spkClarityMode",
        "default": 0,
        "slot": ("group", "clarity", "mode"),
    },
    "clarityGain": {
        "type": "IntPref",
        "spk": "spkClarityGain",
        "default": 50,
        "slot": ("group", "clarity", "gain"),
        "range": (0, 450),
    },
    "cureEnabled": {
        "type": "BoolPref",
        "spk": "spkCureEnabled",
        "default": False,
        "slot": ("group", "cure", "enable"),
    },
    "cureStrength": {
        "type": "IntPref",
        "spk": "spkCureStrength",
        "default": 0,
        "slot": ("group", "cure", "crossfeedPreset"),
    },
    "analogxEnabled": {
        "type": "BoolPref",
        "spk": "spkAnalogxEnabled",
        "default": False,
        "slot": ("group", "analogX", "enable"),
    },
    "analogxMode": {
        "type": "IntPref",
        "spk": "spkAnalogxMode",
        "default": 0,
        "slot": ("group", "analogX", "mode"),
    },
    "speakerOptEnabled": {
        "type": "BoolPref",
        "spk": "speakerOptEnabled",
        "default": False,
        "slot": ("group", "speakerCorrection", "enable"),
    },
}

SLOT_TO_V1: dict[tuple[str, str], str] = {}
for _k, _v in PREF_TABLE.items():
    _slot = _v["slot"]
    if _slot[0] in ("group", "array"):
        SLOT_TO_V1[(_slot[1], _slot[2])] = _k

_BOOL_MAP = {
    "36868": "masterEnabled",
    "65565": "agcEnabled",
    "65610": "fetEnabled",
    "65614": "fetAutoKnee",
    "65616": "fetAutoGain",
    "65618": "fetAutoAttack",
    "65620": "fetAutoRelease",
    "65626": "fetNoClip",
    "65546": "ddcEnabled",
    "65548": "vseEnabled",
    "65551": "eqEnabled",
    "65538": "convolverEnabled",
    "65553": "fieldSurroundEnabled",
    "65557": "diffSurroundEnabled",
    "65544": "vheEnabled",
    "65559": "reverbEnabled",
    "65569": "dynamicSystemEnabled",
    "65583": "tubeSimulatorEnabled",
    "65574": "bassEnabled",
    "65578": "clarityEnabled",
    "65581": "cureEnabled",
    "65584": "analogxEnabled",
    "65603": "speakerOptEnabled",
}
_INT_MAP = {
    "65611": "fetThreshold",
    "65612": "fetRatio",
    "65613": "fetKnee",
    "65615": "fetGain",
    "65617": "fetAttack",
    "65619": "fetRelease",
    "65621": "fetKneeMulti",
    "65622": "fetMaxAttack",
    "65623": "fetMaxRelease",
    "65624": "fetCrest",
    "65625": "fetAdapt",
    "65543": "convolverCrossChannel",
    "65555": "fieldSurroundMidImage",
    "65554;65556": "fieldSurroundWidening",
    "65558": "diffSurroundDelay",
    "65545": "vheQuality",
    "65560": "reverbRoomSize",
    "65561": "reverbWidth",
    "65562": "reverbDampening",
    "65563": "reverbWet",
    "65564": "reverbDry",
    "65573": "dynamicSystemStrength",
    "65576": "bassFrequency",
    "65582": "cureStrength",
    "65585": "analogxMode",
}
_STR_MAP = {
    "65552": "eqBands",
    "65547": "ddcDevice",
    "65540;65541;65542": "convolverKernel",
}
_MODE_MAP = {"65575": "bassMode", "65579": "clarityMode"}

_BARE_AMP = re.compile(r"&(?!(?:amp|lt|gt|quot|apos|#\d+|#x[0-9a-fA-F]+);)")


def _repair_xml(content: str) -> str:
    """Make real-world (often malformed) ViPER presets well-formed for ET."""
    content = content.replace("></string>amp;", "&amp;</string>")
    return _BARE_AMP.sub("&amp;", content)


def xml_is_viper(content: str) -> bool:
    """Legacy ViPER preset if it carries the master-enable param (36868)."""
    return 'name="36868"' in content


def _xml_parse(content: str) -> dict[str, str]:
    """Parse the ``<map>`` body into id -> value using a real XML parser."""
    root = ET.fromstring(_repair_xml(content))
    xv: dict[str, str] = {}
    for el in root.iter():
        name = el.get("name")
        if name is None or el.tag not in ("int", "boolean", "string"):
            continue
        if el.tag == "string":
            xv[name] = el.text or ""
        else:
            xv[name] = el.get("value") or ""
    return xv


def _xml_normalize(xv: dict[str, str]) -> None:
    """Bring older preset layouts up to the v2.7.2.x id/scale conventions."""

    def adopt(old: str, new: str) -> None:
        if old in xv and new not in xv:
            xv[new] = xv[old]

    def adopt_room(old: str, new: str) -> None:
        raw = xv.get(new, xv.get(old))
        n = _to_int(raw)
        if n is None:
            return
        xv[new] = str(n // 10 if n > 10 else n)

    for j in range(17):
        adopt(str(65627 + j), str(65610 + j))
    adopt("65595", "65551")
    adopt("65596", "65552")
    adopt("65589", "65538")
    adopt("65591;65592;65593", "65540;65541;65542")
    adopt("65594", "65543")
    adopt("65597", "65559")
    adopt("65600", "65562")
    adopt("65601", "65563")
    adopt("65602", "65564")
    adopt_room("65598", "65560")
    adopt_room("65599", "65561")

    def rescale(key: str, cap: int, f) -> None:
        n = _to_int(xv.get(key))
        if n is None:
            return
        if n > cap:
            xv[key] = str(f(n))

    rescale("65554;65556", 8, lambda it: (it - 120) // 10)
    rescale("65555", 10, lambda it: (it - 120) // 10)
    rescale("65558", 19, lambda it: it // 100 - 1)
    rescale("65573", 100, lambda it: (it - 100) // 20)
    rescale("65576", 135, lambda it: it - 15)
    rescale("65577", 11, lambda it: (it - 50) // 50)
    rescale("65580", 9, lambda it: it // 50)


def _to_int(raw: str | None) -> int | None:
    if raw is None:
        return None
    raw = raw.strip()
    try:
        return int(raw)
    except ValueError:
        try:
            return int(float(raw))
        except ValueError:
            return None


def xml_to_v1(content: str) -> dict[str, Any]:
    """Translate a legacy ViPER xml preset into the v1 flat json dict."""
    xv = _xml_parse(content)
    _xml_normalize(xv)
    v1: dict[str, Any] = {}

    def set_key(json_key: str, value: Any) -> None:
        if json_key in PREF_TABLE:
            v1[json_key] = value

    for xid, key in _BOOL_MAP.items():
        if xid in xv:
            set_key(key, xv[xid].strip() == "true")
    for xid, key in _INT_MAP.items():
        n = _to_int(xv.get(xid))
        if n is not None:
            set_key(key, n)
    for xid, key in _STR_MAP.items():
        if xid in xv:
            set_key(key, xv[xid])
    for xid, key in _MODE_MAP.items():
        n = _to_int(xv.get(xid))
        if n is not None:
            set_key(key, n)

    n = _to_int(xv.get("65577"))
    if n is not None:
        set_key("bassGain", min(max(n * 50 + 50, 50), 1000))
    n = _to_int(xv.get("65580"))
    if n is not None:
        set_key("clarityGain", min(max(n * 50, 0), 450))
    n = _to_int(xv.get("65549;65550"))
    if n is not None:
        set_key("vseStrength", min(max(2200 + n * 600, 2200), 8200))

    ds = xv.get("65570;65571;65572", "")
    if ds:
        parts = ds.split(";")
        if len(parts) >= 6:
            for idx, k in enumerate(
                ["dsXLow", "dsXHigh", "dsYLow", "dsYHigh", "dsSideGainLow", "dsSideGainHigh"]
            ):
                pv = _to_int(parts[idx])
                if pv is not None:
                    set_key(k, pv)
            set_key("dynamicSystemDevice", 0)

    set_key("eqBandCount", 10)
    return v1


def _coerce_scalar(
    pref_type: str,
    value: Any,
    default: Any,
    value_range: tuple[int, int] | None = None,
) -> Any:
    """Read a v1 scalar as the pref's native type, clamped to the pref's range."""
    if pref_type == "BoolPref":
        if isinstance(value, bool):
            return value
        if isinstance(value, str):
            return value.strip().lower() == "true"
        return bool(value)
    if pref_type == "IntPref":
        n = _to_int(str(value)) if not isinstance(value, bool) else None
        n = n if n is not None else default
        if value_range is not None and isinstance(n, int):
            lo, hi = value_range
            n = max(lo, min(hi, n))
        return n
    if pref_type == "StringPref":
        return value if isinstance(value, str) else str(value)
    if pref_type == "NullableLongPref":
        n = _to_int(str(value)) if not isinstance(value, bool) else None
        return n if (n is not None and n >= 0) else None
    return value


def _parse_v1_array(
    raw: str,
    kind: str,
    value_range: tuple[float, float] | None = None,
) -> list[Any]:
    """Split a ";"-joined v1 array string into native values, clamped to range."""
    out: list[Any] = []
    if not raw:
        return out
    for token in raw.split(";"):
        if token == "":
            continue
        if kind == "INT":
            n = _to_int(token)
            v: Any = n if n is not None else 0
        elif kind == "BOOL01":
            out.append(token == "1" or token == "true")
            continue
        elif kind == "DOUBLE":
            try:
                v = float(token)
            except ValueError:
                v = 0.0
        else:
            continue
        if value_range is not None:
            lo, hi = value_range
            v = max(lo, min(hi, v))
        out.append(v)
    return out


def v1_to_v2(
    v1: dict[str, Any],
    is_spk: bool,
    *,
    name: str | None = None,
    fill_defaults: bool = True,
) -> dict[str, Any]:
    """Convert v1 flat json to v2 grouped json."""
    out: dict[str, Any] = {"schemaVersion": 2}
    if name is not None:
        out["name"] = name

    for group_name, fields in GROUP_ORDER.items():
        group_obj: dict[str, Any] = {}
        for field in fields:
            v1_key = SLOT_TO_V1.get((group_name, field))
            if v1_key is None:
                continue
            info = PREF_TABLE[v1_key]
            src_key = info["spk"] if is_spk else v1_key
            present = src_key in v1
            if not present and not fill_defaults:
                continue
            slot = info["slot"]
            if slot[0] == "array":
                raw = v1.get(src_key, info["default"])
                raw = raw if isinstance(raw, str) else info["default"]
                group_obj[field] = _parse_v1_array(raw, slot[3], info.get("range"))
            else:
                raw = v1.get(src_key, info["default"])
                group_obj[field] = _coerce_scalar(
                    info["type"], raw, info["default"], info.get("range")
                )
        if group_obj:
            out[group_name] = group_obj
    return out


def _detect_format(text: str) -> str:
    stripped = text.lstrip()
    if stripped.startswith("<"):
        return "xml"
    return "v1json"


def convert(
    text: str,
    *,
    fmt: str,
    name: str | None,
    fill_defaults: bool,
) -> dict[str, Any]:
    if fmt == "xml":
        if not xml_is_viper(text):
            raise ValueError("input is XML but not a recognised ViPER preset (no param 36868)")
        return v1_to_v2(xml_to_v1(text), False, name=name, fill_defaults=fill_defaults)
    v1 = json.loads(text)
    if not isinstance(v1, dict):
        raise ValueError("v1 json must be a JSON object")
    is_spk = "spkMasterEnabled" in v1
    return v1_to_v2(v1, is_spk, name=name, fill_defaults=fill_defaults)


def _build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description="Convert ViPER4Android v1 JSON / legacy XML presets to v2 grouped JSON.",
    )
    p.add_argument(
        "input", type=Path, help="input preset file (v1 .json or legacy .xml); '-' for stdin"
    )
    p.add_argument("-o", "--output", type=Path, default=None, help="output file (default: stdout)")
    fmt = p.add_mutually_exclusive_group()
    fmt.add_argument(
        "--xml", dest="fmt", action="store_const", const="xml", help="force legacy-XML input"
    )
    fmt.add_argument(
        "--v1", dest="fmt", action="store_const", const="v1json", help="force v1-JSON input"
    )
    p.set_defaults(fmt=None)
    p.add_argument("--name", default=None, help="preset name to embed in v2 output")
    p.add_argument(
        "--no-fill-defaults",
        dest="fill_defaults",
        action="store_false",
        help="do not fill missing fields with defaults",
    )
    p.set_defaults(fill_defaults=True)
    return p


def main(argv: list[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)

    if str(args.input) == "-":
        text = sys.stdin.read()
    else:
        text = args.input.read_text(encoding="utf-8")

    fmt = args.fmt or _detect_format(text)

    try:
        v2 = convert(
            text,
            fmt=fmt,
            name=args.name,
            fill_defaults=args.fill_defaults,
        )
    except (ValueError, json.JSONDecodeError) as e:
        print(f"error: {e}", file=sys.stderr)
        return 2

    rendered = json.dumps(v2, indent=2, ensure_ascii=False)
    if args.output is not None:
        args.output.write_text(rendered + "\n", encoding="utf-8")
        print(f"wrote {args.output} (fmt={fmt})", file=sys.stderr)
    else:
        print(rendered)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
