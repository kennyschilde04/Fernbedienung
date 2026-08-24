package de.lightweb.fernbedienung.proto

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Minimaler Protobuf-Encoder/Decoder.
 *
 * Das Android-TV-Remote-Protokoll (v2) benutzt nur sehr einfache Nachrichten
 * (varint, string, bytes, verschachtelte Messages), deshalb reicht diese
 * handgeschriebene Implementierung und wir sparen uns die protoc-Toolchain.
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long): ProtoWriter {
        tag(field, 0)
        writeVarint(out, value)
        return this
    }

    fun int(field: Int, value: Int): ProtoWriter = varint(field, value.toLong())

    fun bool(field: Int, value: Boolean): ProtoWriter = varint(field, if (value) 1L else 0L)

    fun bytes(field: Int, value: ByteArray): ProtoWriter {
        tag(field, 2)
        writeVarint(out, value.size.toLong())
        out.write(value)
        return this
    }

    fun string(field: Int, value: String): ProtoWriter = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, block: ProtoWriter.() -> Unit): ProtoWriter {
        val nested = ProtoWriter()
        nested.block()
        return bytes(field, nested.toByteArray())
    }

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wireType: Int) = writeVarint(out, ((field shl 3) or wireType).toLong())

    companion object {
        fun message(block: ProtoWriter.() -> Unit): ByteArray {
            val w = ProtoWriter()
            w.block()
            return w.toByteArray()
        }

        fun writeVarint(out: OutputStream, value: Long) {
            var v = value
            while (true) {
                val b = (v and 0x7F).toInt()
                v = v ushr 7
                if (v == 0L) {
                    out.write(b)
                    return
                }
                out.write(b or 0x80)
            }
        }
    }
}

/** Eine geparste Protobuf-Nachricht: Feldnummer -> Werte. */
class ProtoMessage(private val fields: Map<Int, List<Any>>) {

    fun has(field: Int): Boolean = fields.containsKey(field)

    fun long(field: Int, default: Long = 0L): Long =
        (fields[field]?.firstOrNull() as? Long) ?: default

    fun int(field: Int, default: Int = 0): Int = long(field, default.toLong()).toInt()

    fun bool(field: Int, default: Boolean = false): Boolean =
        if (has(field)) long(field) != 0L else default

    fun bytes(field: Int): ByteArray? = fields[field]?.firstOrNull() as? ByteArray

    fun string(field: Int, default: String = ""): String =
        bytes(field)?.toString(Charsets.UTF_8) ?: default

    fun message(field: Int): ProtoMessage? = bytes(field)?.let { parse(it) }

    fun fieldNumbers(): Set<Int> = fields.keys

    override fun toString(): String = fields.keys.sorted().joinToString(prefix = "Msg(", postfix = ")")

    companion object {
        fun parse(data: ByteArray): ProtoMessage {
            val map = LinkedHashMap<Int, MutableList<Any>>()
            var pos = 0
            while (pos < data.size) {
                val (tag, p1) = readVarint(data, pos)
                pos = p1
                val field = (tag ushr 3).toInt()
                when ((tag and 0x7L).toInt()) {
                    0 -> {
                        val (v, p2) = readVarint(data, pos)
                        pos = p2
                        map.getOrPut(field) { mutableListOf() }.add(v)
                    }
                    1 -> {
                        var v = 0L
                        for (i in 0 until 8) v = v or ((data[pos + i].toLong() and 0xFF) shl (8 * i))
                        pos += 8
                        map.getOrPut(field) { mutableListOf() }.add(v)
                    }
                    2 -> {
                        val (len, p2) = readVarint(data, pos)
                        pos = p2
                        val end = pos + len.toInt()
                        map.getOrPut(field) { mutableListOf() }.add(data.copyOfRange(pos, end))
                        pos = end
                    }
                    5 -> {
                        var v = 0L
                        for (i in 0 until 4) v = v or ((data[pos + i].toLong() and 0xFF) shl (8 * i))
                        pos += 4
                        map.getOrPut(field) { mutableListOf() }.add(v)
                    }
                    else -> throw IllegalArgumentException("Unbekannter Wire-Type in Feld $field")
                }
            }
            return ProtoMessage(map)
        }

        private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int> {
            var result = 0L
            var shift = 0
            var pos = start
            while (true) {
                if (pos >= data.size) throw IllegalArgumentException("Varint abgeschnitten")
                val b = data[pos].toInt() and 0xFF
                pos++
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result to pos
                shift += 7
                if (shift > 63) throw IllegalArgumentException("Varint zu lang")
            }
        }
    }
}

/**
 * Rahmenformat des Protokolls: varint mit der Laenge, danach die Nachricht.
 */
object Frames {
    fun write(out: OutputStream, message: ByteArray) {
        ProtoWriter.writeVarint(out, message.size.toLong())
        out.write(message)
        out.flush()
    }

    fun read(input: InputStream): ByteArray {
        var length = 0L
        var shift = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException("Verbindung geschlossen")
            length = length or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) break
            shift += 7
            if (shift > 35) throw IllegalArgumentException("Ungueltige Laenge")
        }
        val buffer = ByteArray(length.toInt())
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) throw EOFException("Verbindung geschlossen")
            read += n
        }
        return buffer
    }
}
