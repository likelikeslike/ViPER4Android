package com.llsl.viper4android

import android.app.Application
import android.util.Property
import com.llsl.viper4android.utils.FileLogger
import dagger.hilt.android.HiltAndroidApp
import java.lang.reflect.Method

@HiltAndroidApp
class ViPER4AndroidApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FileLogger.init(this)
    }

    companion object {
        init {
            try {
                val methods = Property.of(
                    Class::class.java,
                    Array<Method>::class.java,
                    "Methods"
                ).get(Class.forName("dalvik.system.VMRuntime"))
                var runtime: Any? = null
                var exemptionMethod: Method? = null
                for (method in methods) {
                    when (method.name) {
                        "getRuntime" -> runtime = method.invoke(null)
                        "setHiddenApiExemptions" -> exemptionMethod = method
                    }
                }
                if (runtime != null && exemptionMethod != null) {
                    exemptionMethod.invoke(runtime, arrayOf("L"))
                    FileLogger.d("App", "unseal: success")
                } else {
                    FileLogger.e(
                        "App",
                        "unseal: methods not found (runtime=$runtime, exemption=$exemptionMethod)"
                    )
                }
            } catch (e: Exception) {
                FileLogger.e("App", "unseal: failed", e)
            }
        }
    }
}
