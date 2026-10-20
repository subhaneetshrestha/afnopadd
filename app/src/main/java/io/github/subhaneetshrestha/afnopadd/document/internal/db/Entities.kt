package io.github.subhaneetshrestha.afnopadd.document.internal.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One element row. The common fields are plain columns and only the kind-specific part is JSON
 * (`ElementJsonCodec.encodePayload`), which keeps the text to parse on open small. Deleted
 * elements keep a tombstone row until compaction.
 */
@Entity(tableName = "element", indices = [Index("z"), Index("deleted")])
data class ElementEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val z: String,
    @ColumnInfo(name = "anchor_x") val anchorX: Double,
    @ColumnInfo(name = "anchor_y") val anchorY: Double,
    @ColumnInfo(name = "frame_level") val frameLevel: Int,
    @ColumnInfo(name = "bounds_left") val boundsLeft: Double,
    @ColumnInfo(name = "bounds_top") val boundsTop: Double,
    @ColumnInfo(name = "bounds_right") val boundsRight: Double,
    @ColumnInfo(name = "bounds_bottom") val boundsBottom: Double,
    val version: Int,
    @ColumnInfo(name = "version_nonce") val versionNonce: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
)

/** Packed stroke samples for one stroke element (`StrokePoints.encode`). */
@Entity(
    tableName = "stroke_points",
    foreignKeys = [
        ForeignKey(
            entity = ElementEntity::class,
            parentColumns = ["id"],
            childColumns = ["element_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class StrokePointsEntity(
    @PrimaryKey @ColumnInfo(name = "element_id") val elementId: String,
    @ColumnInfo(name = "format_version") val formatVersion: Int,
    val blob: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is StrokePointsEntity && elementId == other.elementId &&
                    formatVersion == other.formatVersion && blob.contentEquals(other.blob)
                )

    override fun hashCode(): Int = 31 * (31 * elementId.hashCode() + formatVersion) + blob.contentHashCode()
}

/** Metadata for a content-addressed asset file stored under the sheet's `assets/` directory. */
@Entity(tableName = "asset")
data class AssetEntity(
    @PrimaryKey val id: String,
    val mime: String,
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** One diff of a committed transaction; the append-only log behind recovery and future sync. */
@Entity(tableName = "op", indices = [Index("txn_id")])
data class OpEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    @ColumnInfo(name = "txn_id") val txnId: String,
    val kind: String,
    @ColumnInfo(name = "inverse_of") val inverseOf: String?,
    val label: String,
    @ColumnInfo(name = "committed_at") val committedAt: Long,
    @ColumnInfo(name = "element_id") val elementId: String?,
    val op: String,
    @ColumnInfo(name = "before_json") val beforeJson: String?,
    @ColumnInfo(name = "after_json") val afterJson: String?,
)
