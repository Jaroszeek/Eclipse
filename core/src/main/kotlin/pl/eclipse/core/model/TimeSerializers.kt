package pl.eclipse.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

// kotlinx.serialization nie zna typów java.time — zapisujemy je jako tekst ISO (np. "2026-09-30").

open class IsoSerializer<T : Any>(name: String, private val parse: (String) -> T) : KSerializer<T> {
    override val descriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): T = parse(decoder.decodeString())
}

object LocalDateSerializer : IsoSerializer<LocalDate>("LocalDate", LocalDate::parse)
object LocalTimeSerializer : IsoSerializer<LocalTime>("LocalTime", LocalTime::parse)
object InstantSerializer : IsoSerializer<Instant>("Instant", Instant::parse)
