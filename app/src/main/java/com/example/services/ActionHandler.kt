package com.example.services

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.models.AgentAction
import com.example.models.AgentActionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ActionHandler(
    private val context: Context,
    private val screenAutomation: ScreenAutomationService,
    private val appLauncher: AppLauncherService
) {
    companion object {
        private const val TAG = "ActionHandler"
    }

    suspend fun execute(action: AgentAction): AgentActionResult = withContext(Dispatchers.IO) {
        try {
            when (action.action.lowercase()) {
                "open_app" -> {
                    val appName = action.getStringParam("app_name")
                    if (appName.isBlank()) {
                        AgentActionResult(false, "Please specify an app name to open.")
                    } else {
                        val (ok, msg) = appLauncher.openAppByName(appName)
                        AgentActionResult(ok, msg)
                    }
                }

                "launch_package" -> {
                    val pkg = action.getStringParam("package_name")
                    if (pkg.isBlank()) {
                        AgentActionResult(false, "Please specify a package name.")
                    } else {
                        val (ok, msg) = appLauncher.launchPackage(pkg)
                        AgentActionResult(ok, msg)
                    }
                }

                "make_call" -> {
                    val contactName = action.getStringParam("contact_name")
                    val phoneNumber = action.getStringParam("phone_number")
                    val numberToCall = if (phoneNumber.isNotBlank()) {
                        phoneNumber
                    } else if (contactName.isNotBlank()) {
                        resolveContactNumber(contactName)
                    } else {
                        null
                    }

                    if (numberToCall.isNullOrBlank()) {
                        AgentActionResult(false, "Could not find a phone number for \"$contactName\".")
                    } else {
                        val hasCallPermission = ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.CALL_PHONE
                        ) == PackageManager.PERMISSION_GRANTED

                        val intentAction = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
                        val callIntent = Intent(intentAction).apply {
                            data = Uri.parse("tel:${Uri.encode(numberToCall)}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(callIntent)
                        val target = if (contactName.isNotBlank()) contactName else numberToCall
                        AgentActionResult(true, "Initiating call to $target ($numberToCall).")
                    }
                }

                "send_sms" -> {
                    val contactName = action.getStringParam("contact_name")
                    val phoneNumber = action.getStringParam("phone_number")
                    val message = action.getStringParam("message")

                    val numberToSend = if (phoneNumber.isNotBlank()) {
                        phoneNumber
                    } else if (contactName.isNotBlank()) {
                        resolveContactNumber(contactName)
                    } else {
                        null
                    }

                    if (numberToSend.isNullOrBlank()) {
                        AgentActionResult(false, "Could not find a recipient number.")
                    } else {
                        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("smsto:${Uri.encode(numberToSend)}")
                            putExtra("sms_body", message)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(smsIntent)
                        val target = if (contactName.isNotBlank()) contactName else numberToSend
                        AgentActionResult(true, "Composing SMS to $target: \"$message\".")
                    }
                }

                "search_contact" -> {
                    val query = action.getStringParam("query")
                    val results = searchContacts(query)
                    if (results.isEmpty()) {
                        AgentActionResult(true, "No contacts found matching \"$query\".")
                    } else {
                        val formatted = results.joinToString("\n") { "• ${it.first}: ${it.second}" }
                        AgentActionResult(true, "Found ${results.size} contact(s):\n$formatted", data = results)
                    }
                }

                "set_alarm" -> {
                    val hour = action.getIntParam("hour", -1)
                    val minute = action.getIntParam("minute", 0)
                    val label = action.getStringParam("label", "EVA AI Alarm")

                    if (hour in 0..23 && minute in 0..59) {
                        val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                            putExtra(AlarmClock.EXTRA_HOUR, hour)
                            putExtra(AlarmClock.EXTRA_MINUTES, minute)
                            putExtra(AlarmClock.EXTRA_MESSAGE, label)
                            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(alarmIntent)
                            val timeStr = String.format("%02d:%02d", hour, minute)
                            AgentActionResult(true, "Set alarm for $timeStr with label \"$label\".")
                        } catch (e: Exception) {
                            AgentActionResult(false, "Could not open clock app: ${e.message}")
                        }
                    } else {
                        AgentActionResult(false, "Invalid alarm time provided ($hour:$minute).")
                    }
                }

                "set_volume" -> {
                    val level = action.getIntParam("level", -1)
                    if (level in 0..100) {
                        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val target = (level / 100f * maxVol).toInt()
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
                        AgentActionResult(true, "Volume set to $level%.")
                    } else {
                        AgentActionResult(false, "Volume level must be between 0 and 100.")
                    }
                }

                "set_brightness" -> {
                    val level = action.getIntParam("level", -1)
                    if (level in 0..100) {
                        try {
                            val canWrite = Settings.System.canWrite(context)
                            if (canWrite) {
                                val value = (level / 100f * 255).toInt()
                                Settings.System.putInt(
                                    context.contentResolver,
                                    Settings.System.SCREEN_BRIGHTNESS,
                                    value
                                )
                                AgentActionResult(true, "Screen brightness set to $level%.")
                            } else {
                                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                                AgentActionResult(false, "Write Settings permission required. Opening system settings.")
                            }
                        } catch (e: Exception) {
                            AgentActionResult(false, "Failed to adjust brightness: ${e.message}")
                        }
                    } else {
                        AgentActionResult(false, "Brightness level must be between 0 and 100.")
                    }
                }

                "read_screen" -> {
                    if (!screenAutomation.isAccessibilityActive) {
                        AgentActionResult(
                            false,
                            "Accessibility service is not enabled. Please enable EVA AI in Accessibility Settings."
                        )
                    } else {
                        val desc = screenAutomation.getScreenDescription(compress = true)
                        AgentActionResult(true, "Screen Inspection:\n$desc")
                    }
                }

                "press_back" -> {
                    if (!screenAutomation.isAccessibilityActive) {
                        AgentActionResult(false, "Accessibility service is inactive.")
                    } else {
                        val ok = screenAutomation.pressBack()
                        AgentActionResult(ok, if (ok) "Pressed Back." else "Failed to press Back.")
                    }
                }

                else -> {
                    AgentActionResult(false, "Unrecognized device action: ${action.action}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing action ${action.action}", e)
            AgentActionResult(false, "Error executing ${action.action}: ${e.message}")
        }
    }

    private fun resolveContactNumber(nameQuery: String): String? {
        val matches = searchContacts(nameQuery)
        return matches.firstOrNull()?.second
    }

    private fun searchContacts(query: String): List<Pair<String, String>> {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return emptyList()

        val results = mutableListOf<Pair<String, String>>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.let {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = it.getString(nameIdx) ?: "Unknown"
                    val number = it.getString(numIdx) ?: ""
                    if (number.isNotBlank()) {
                        results.add(Pair(name, number))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Contacts query error", e)
        } finally {
            cursor?.close()
        }
        return results
    }
}
