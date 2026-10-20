package com.afec.app.data

import androidx.room.TypeConverter

/** Room converters: enums as names, Int lists as comma-separated text (PRD JSON columns, kept dependency-free). */
class Converters {
    @TypeConverter fun typeToString(v: InstrumentType): String = v.name
    @TypeConverter fun stringToType(v: String): InstrumentType =
        runCatching { InstrumentType.valueOf(v) }.getOrDefault(InstrumentType.OTHER)

    @TypeConverter fun sourceToString(v: InstrumentSource): String = v.name
    @TypeConverter fun stringToSource(v: String): InstrumentSource =
        runCatching { InstrumentSource.valueOf(v) }.getOrDefault(InstrumentSource.MANUAL_ENTRY)

    @TypeConverter fun statusToString(v: InstrumentStatus): String = v.name
    @TypeConverter fun stringToStatus(v: String): InstrumentStatus =
        runCatching { InstrumentStatus.valueOf(v) }.getOrDefault(InstrumentStatus.ACTIVE)

    @TypeConverter fun parseToString(v: ParseResult): String = v.name
    @TypeConverter fun stringToParse(v: String): ParseResult =
        runCatching { ParseResult.valueOf(v) }.getOrDefault(ParseResult.FAILED)

    @TypeConverter fun actionToString(v: SmsUserAction): String = v.name
    @TypeConverter fun stringToAction(v: String): SmsUserAction =
        runCatching { SmsUserAction.valueOf(v) }.getOrDefault(SmsUserAction.PENDING)

    @TypeConverter fun intListToString(v: List<Int>): String = v.joinToString(",")
    @TypeConverter fun stringToIntList(v: String?): List<Int> =
        v?.takeIf { it.isNotBlank() }?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()

    @TypeConverter fun stringListToString(v: List<String>): String = v.joinToString("|")
    @TypeConverter fun stringToStringList(v: String): List<String> =
        if (v.isBlank()) emptyList() else v.split("|")
}
