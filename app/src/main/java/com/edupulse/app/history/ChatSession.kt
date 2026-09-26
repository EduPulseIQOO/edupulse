package com.edupulse.app.history

import com.edupulse.app.diagram.PhysicsDiagram
import com.edupulse.app.ui.ChatMessage
import com.edupulse.app.ui.MessageSender
import com.edupulse.app.ui.OcrFix
import org.json.JSONArray
import org.json.JSONObject

data class ChatSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val subject: String = QuerySubject.GENERAL.name,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val languageCode: String = "ENGLISH",
    val messages: List<ChatMessage> = emptyList()
) {
    val querySubject: QuerySubject
        get() = QuerySubject.fromName(subject)

    val lastMessagePreview: String
        get() {
            val last = messages.lastOrNull() ?: return ""
            val clean = last.text.replace("\n", " ").trim()
            return if (clean.length > 70) clean.take(70) + "..." else clean
        }

    val activeDiagram: PhysicsDiagram?
        get() = messages.asReversed().firstOrNull { it.diagram != null }?.diagram

    val userMessageCount: Int
        get() = messages.count { it.sender == MessageSender.USER }

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("title", title)
        obj.put("subject", subject)
        obj.put("createdAt", createdAt)
        obj.put("updatedAt", updatedAt)
        obj.put("languageCode", languageCode)

        val msgArray = JSONArray()
        for (m in messages) {
            val mObj = JSONObject()
            mObj.put("id", m.id)
            mObj.put("sender", m.sender.name)
            mObj.put("text", m.text)
            mObj.put("timestamp", m.timestamp)

            if (m.ocrFixes.isNotEmpty()) {
                val fixesArr = JSONArray()
                for (f in m.ocrFixes) {
                    val fObj = JSONObject()
                    fObj.put("original", f.original)
                    fObj.put("fixed", f.fixed)
                    fixesArr.put(fObj)
                }
                mObj.put("ocrFixes", fixesArr)
            }

            m.diagram?.let { d ->
                val diagObj = JSONObject()
                when (d) {
                    is PhysicsDiagram.Kinematics -> {
                        diagObj.put("type", "kinematics")
                        diagObj.put("mass", d.mass)
                        diagObj.put("initialVelocity", d.initialVelocity)
                        diagObj.put("finalVelocity", d.finalVelocity)
                        diagObj.put("acceleration", d.acceleration)
                        diagObj.put("force", d.force)
                        diagObj.put("distance", d.distance)
                        diagObj.put("time", d.time)
                        diagObj.put("isDecelerating", d.isDecelerating)
                    }
                    is PhysicsDiagram.FreeBody -> {
                        diagObj.put("type", "freebody")
                        diagObj.put("mass", d.mass)
                        diagObj.put("normalForce", d.normalForce)
                        diagObj.put("gravityForce", d.gravityForce)
                        diagObj.put("appliedForce", d.appliedForce)
                        diagObj.put("frictionForce", d.frictionForce)
                        diagObj.put("netForce", d.netForce)
                    }
                    is PhysicsDiagram.Projectile -> {
                        diagObj.put("type", "projectile")
                        diagObj.put("angle", d.angle)
                        diagObj.put("velocity", d.velocity)
                        diagObj.put("range", d.range)
                        diagObj.put("maxHeight", d.maxHeight)
                        diagObj.put("timeOfFlight", d.timeOfFlight)
                    }
                }
                mObj.put("diagram", diagObj)
            }
            msgArray.put(mObj)
        }
        obj.put("messages", msgArray)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): ChatSession {
            val id = obj.optString("id", java.util.UUID.randomUUID().toString())
            val title = obj.optString("title", "New Chat")
            val subject = obj.optString("subject", QuerySubject.GENERAL.name)
            val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            val updatedAt = obj.optLong("updatedAt", createdAt)
            val languageCode = obj.optString("languageCode", "ENGLISH")

            val messages = mutableListOf<ChatMessage>()
            val msgArray = obj.optJSONArray("messages")
            if (msgArray != null) {
                for (i in 0 until msgArray.length()) {
                    val mObj = msgArray.getJSONObject(i)
                    val senderStr = mObj.optString("sender", "USER")
                    val sender = try {
                        MessageSender.valueOf(senderStr)
                    } catch (_: Exception) {
                        MessageSender.USER
                    }
                    val text = mObj.optString("text", "")
                    val timestamp = mObj.optLong("timestamp", System.currentTimeMillis())
                    val mId = mObj.optString("id", java.util.UUID.randomUUID().toString())

                    val fixes = mutableListOf<OcrFix>()
                    val fixesArr = mObj.optJSONArray("ocrFixes")
                    if (fixesArr != null) {
                        for (j in 0 until fixesArr.length()) {
                            val fObj = fixesArr.getJSONObject(j)
                            fixes.add(OcrFix(fObj.optString("original", ""), fObj.optString("fixed", "")))
                        }
                    }

                    val diagram = if (mObj.has("diagram")) {
                        val d = mObj.getJSONObject("diagram")
                        when (d.optString("type")) {
                            "kinematics" -> PhysicsDiagram.Kinematics(
                                mass = d.optNullableString("mass"),
                                initialVelocity = d.optNullableString("initialVelocity"),
                                finalVelocity = d.optNullableString("finalVelocity"),
                                acceleration = d.optNullableString("acceleration"),
                                force = d.optNullableString("force"),
                                distance = d.optNullableString("distance"),
                                time = d.optNullableString("time"),
                                isDecelerating = d.optBoolean("isDecelerating", true)
                            )
                            "freebody" -> PhysicsDiagram.FreeBody(
                                mass = d.optNullableString("mass"),
                                normalForce = d.optNullableString("normalForce"),
                                gravityForce = d.optNullableString("gravityForce"),
                                appliedForce = d.optNullableString("appliedForce"),
                                frictionForce = d.optNullableString("frictionForce"),
                                netForce = d.optNullableString("netForce")
                            )
                            "projectile" -> PhysicsDiagram.Projectile(
                                angle = d.optNullableString("angle"),
                                velocity = d.optNullableString("velocity"),
                                range = d.optNullableString("range"),
                                maxHeight = d.optNullableString("maxHeight"),
                                timeOfFlight = d.optNullableString("timeOfFlight")
                            )
                            else -> null
                        }
                    } else null

                    messages.add(
                        ChatMessage(
                            id = mId,
                            sender = sender,
                            text = text,
                            ocrFixes = fixes,
                            diagram = diagram,
                            isStreaming = false,
                            timestamp = timestamp
                        )
                    )
                }
            }

            return ChatSession(
                id = id,
                title = title,
                subject = subject,
                createdAt = createdAt,
                updatedAt = updatedAt,
                languageCode = languageCode,
                messages = messages
            )
        }

        private fun JSONObject.optNullableString(key: String): String? {
            return if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
        }
    }
}
