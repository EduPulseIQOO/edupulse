package com.edupulse.app.history

import com.edupulse.app.diagram.PhysicsDiagram
import org.json.JSONObject

data class DoubtHistoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val title: String,
    val question: String,
    val solution: String,
    val diagram: PhysicsDiagram? = null,
    val languageCode: String = "ENGLISH",
    val subject: String = QuerySubject.GENERAL.name
) {
    val querySubject: QuerySubject
        get() = QuerySubject.fromName(subject)

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("timestamp", timestamp)
        obj.put("title", title)
        obj.put("question", question)
        obj.put("solution", solution)
        obj.put("languageCode", languageCode)
        obj.put("subject", subject)

        diagram?.let { d ->
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
                else -> {
                    diagObj.put("type", "other")
                }
            }
            obj.put("diagram", diagObj)
        }
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): DoubtHistoryItem {
            val id = obj.optString("id", java.util.UUID.randomUUID().toString())
            val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            val title = obj.optString("title", "Physics Problem")
            val question = obj.optString("question", "")
            val solution = obj.optString("solution", "")
            val languageCode = obj.optString("languageCode", "ENGLISH")

            val diagram = if (obj.has("diagram")) {
                val d = obj.getJSONObject("diagram")
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

            val subject = if (obj.has("subject")) {
                obj.optString("subject", QuerySubject.GENERAL.name)
            } else {
                QueryClassifier.classify(question, diagram).name
            }

            return DoubtHistoryItem(
                id = id,
                timestamp = timestamp,
                title = title,
                question = question,
                solution = solution,
                diagram = diagram,
                languageCode = languageCode,
                subject = subject
            )
        }

        private fun JSONObject.optNullableString(key: String): String? {
            return if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
        }
    }
}
