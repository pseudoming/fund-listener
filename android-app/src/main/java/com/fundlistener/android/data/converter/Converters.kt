package com.fundlistener.android.data.converter

import androidx.room.TypeConverter
import java.math.BigDecimal

/**
 * Room TypeConverter — BigDecimal ↔ String。
 * Room 不原生支持 BigDecimal，通过 String 序列化中转。
 */
class BigDecimalConverters {

    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? = value?.toBigDecimalOrNull()
}
