package io.github.subhaneetshrestha.afnopadd.canvas.internal

import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * An incremental R-tree (Guttman, quadratic split) over double-precision rectangles keyed by [K].
 * Every entry remembers its leaf and every node its parent, so moving or removing a key never
 * searches the tree. Rectangles that merely touch count as intersecting. Not thread-safe.
 */
internal class RTree<K : Any>(private val maxEntries: Int = 16) {

    init {
        require(maxEntries >= 4) { "maxEntries must be at least 4" }
    }

    private val minEntries = maxOf(2, maxEntries * 2 / 5)

    private abstract class Item {
        var minX = 0.0
        var minY = 0.0
        var maxX = 0.0
        var maxY = 0.0

        fun area() = (maxX - minX) * (maxY - minY)

        fun intersects(left: Double, top: Double, right: Double, bottom: Double) =
            minX <= right && left <= maxX && minY <= bottom && top <= maxY

        fun enlargedArea(other: Item) =
            (maxOf(maxX, other.maxX) - minOf(minX, other.minX)) * (maxOf(maxY, other.maxY) - minOf(minY, other.minY))
    }

    private class Entry<K>(val key: K, rect: WorldRect) : Item() {
        lateinit var leaf: Node

        init {
            minX = rect.left
            minY = rect.top
            maxX = rect.right
            maxY = rect.bottom
        }
    }

    private class Node(val isLeaf: Boolean) : Item() {
        var parent: Node? = null
        val children = ArrayList<Item>()

        fun add(child: Item) {
            children += child
            adopt(child)
        }

        fun adopt(child: Item) {
            if (child is Node) child.parent = this else (child as Entry<*>).leaf = this
        }

        fun recomputeBounds() {
            if (children.isEmpty()) {
                minX = 0.0; minY = 0.0; maxX = 0.0; maxY = 0.0
                return
            }
            minX = Double.POSITIVE_INFINITY; minY = Double.POSITIVE_INFINITY
            maxX = Double.NEGATIVE_INFINITY; maxY = Double.NEGATIVE_INFINITY
            for (child in children) {
                if (child.minX < minX) minX = child.minX
                if (child.minY < minY) minY = child.minY
                if (child.maxX > maxX) maxX = child.maxX
                if (child.maxY > maxY) maxY = child.maxY
            }
        }
    }

    private var root = Node(isLeaf = true)
    private val entries = HashMap<K, Entry<K>>()

    val size: Int get() = entries.size

    fun bounds(key: K): WorldRect? = entries[key]?.let { WorldRect(it.minX, it.minY, it.maxX, it.maxY) }

    /** Adds [key] with [rect], or moves it there if it is already present. */
    fun insert(key: K, rect: WorldRect) {
        remove(key)
        val entry = Entry(key, rect)
        entries[key] = entry
        insertEntry(entry)
    }

    fun remove(key: K): Boolean {
        val entry = entries.remove(key) ?: return false
        val leaf = entry.leaf
        leaf.children.remove(entry)
        condense(leaf)
        return true
    }

    fun clear() {
        entries.clear()
        root = Node(isLeaf = true)
    }

    /**
     * Replaces the contents with [items] by sort-tile-recursive packing: one sort per level instead
     * of a descent and possible split per entry, so opening a large sheet stays cheap. A key listed
     * twice keeps its last rectangle.
     */
    fun load(items: List<Pair<K, WorldRect>>) {
        clear()
        for ((key, rect) in items) entries[key] = Entry(key, rect)
        if (entries.isEmpty()) return
        var level: List<Item> = entries.values.toList()
        var isLeaf = true
        while (true) {
            val nodes = pack(level, isLeaf)
            if (nodes.size == 1) {
                root = nodes[0]
                root.parent = null
                return
            }
            level = nodes
            isLeaf = false
        }
    }

    /** Groups [items] into nodes of up to [maxEntries]: vertical slices by x, then runs by y within each slice. */
    private fun pack(items: List<Item>, isLeaf: Boolean): List<Node> {
        val nodeCount = ceil(items.size / maxEntries.toDouble()).toInt()
        val sliceSize = ceil(sqrt(nodeCount.toDouble())).toInt() * maxEntries
        val nodes = ArrayList<Node>(nodeCount)
        for (slice in items.sortedBy { it.minX + it.maxX }.chunked(sliceSize)) {
            for (group in slice.sortedBy { it.minY + it.maxY }.chunked(maxEntries)) {
                val node = Node(isLeaf)
                group.forEach { node.add(it) }
                node.recomputeBounds()
                nodes += node
            }
        }
        return nodes
    }

    fun query(rect: WorldRect): List<K> {
        val result = ArrayList<K>()
        query(rect) { result += it }
        return result
    }

    fun query(rect: WorldRect, onHit: (K) -> Unit) {
        if (entries.isEmpty()) return
        val left = rect.left
        val top = rect.top
        val right = rect.right
        val bottom = rect.bottom
        val stack = ArrayList<Node>()
        stack += root
        while (stack.isNotEmpty()) {
            val node = stack.removeAt(stack.lastIndex)
            if (!node.intersects(left, top, right, bottom)) continue
            for (child in node.children) {
                if (!child.intersects(left, top, right, bottom)) continue
                @Suppress("UNCHECKED_CAST")
                if (node.isLeaf) onHit((child as Entry<K>).key) else stack += child as Node
            }
        }
    }

    private fun insertEntry(entry: Entry<K>) {
        var node = root
        while (!node.isLeaf) node = chooseChild(node, entry)
        node.add(entry)
        var current: Node? = node
        while (current != null) {
            if (current.children.size > maxEntries) split(current)
            current.recomputeBounds()
            current = current.parent
        }
    }

    /** The child needing the least enlargement to hold [item]; ties go to the smaller child. */
    private fun chooseChild(node: Node, item: Item): Node {
        var best: Node? = null
        var bestEnlargement = Double.POSITIVE_INFINITY
        var bestArea = Double.POSITIVE_INFINITY
        for (child in node.children) {
            child as Node
            val area = child.area()
            val enlargement = child.enlargedArea(item) - area
            if (enlargement < bestEnlargement || (enlargement == bestEnlargement && area < bestArea)) {
                best = child
                bestEnlargement = enlargement
                bestArea = area
            }
        }
        return checkNotNull(best) { "Inner node without children" }
    }

    /** Splits an overfull [node] in two, adding the new sibling to its parent (or a new root). */
    private fun split(node: Node) {
        val items = ArrayList(node.children)
        node.children.clear()
        val sibling = Node(node.isLeaf)

        // Quadratic seeds: the pair that would waste the most area together.
        var seedA = 0
        var seedB = 1
        var worst = Double.NEGATIVE_INFINITY
        for (i in items.indices) {
            for (j in i + 1 until items.size) {
                val waste = items[i].enlargedArea(items[j]) - items[i].area() - items[j].area()
                if (waste > worst) {
                    worst = waste
                    seedA = i
                    seedB = j
                }
            }
        }
        node.add(items[seedA])
        sibling.add(items[seedB])
        node.recomputeBounds()
        sibling.recomputeBounds()
        val remaining = items.filterIndexedTo(ArrayList()) { i, _ -> i != seedA && i != seedB }

        while (remaining.isNotEmpty()) {
            // If one group must take everything left to reach the minimum, give it all.
            if (node.children.size + remaining.size == minEntries) {
                remaining.forEach { node.add(it) }
                break
            }
            if (sibling.children.size + remaining.size == minEntries) {
                remaining.forEach { sibling.add(it) }
                break
            }
            // Next: the item with the strongest preference for one group.
            var pick = 0
            var bestDifference = Double.NEGATIVE_INFINITY
            for (i in remaining.indices) {
                val growA = node.enlargedArea(remaining[i]) - node.area()
                val growB = sibling.enlargedArea(remaining[i]) - sibling.area()
                val difference = abs(growA - growB)
                if (difference > bestDifference) {
                    bestDifference = difference
                    pick = i
                }
            }
            val item = remaining.removeAt(pick)
            val growA = node.enlargedArea(item) - node.area()
            val growB = sibling.enlargedArea(item) - sibling.area()
            val target = when {
                growA < growB -> node
                growB < growA -> sibling
                node.area() < sibling.area() -> node
                sibling.area() < node.area() -> sibling
                node.children.size <= sibling.children.size -> node
                else -> sibling
            }
            target.add(item)
            target.recomputeBounds()
        }
        node.recomputeBounds()
        sibling.recomputeBounds()

        val parent = node.parent
        if (parent == null) {
            val newRoot = Node(isLeaf = false)
            newRoot.add(node)
            newRoot.add(sibling)
            newRoot.recomputeBounds()
            root = newRoot
        } else {
            parent.add(sibling)
        }
    }

    /** After a removal: drops underfull nodes on the path to the root and reinserts their entries. */
    private fun condense(start: Node) {
        val orphans = ArrayList<Entry<K>>()
        var node = start
        while (true) {
            val parent = node.parent ?: break
            if (node.children.size < minEntries) {
                parent.children.remove(node)
                node.parent = null
                collectEntries(node, orphans)
            } else {
                node.recomputeBounds()
            }
            node = parent
        }
        root.recomputeBounds()
        while (!root.isLeaf && root.children.size == 1) {
            root = root.children[0] as Node
            root.parent = null
        }
        if (!root.isLeaf && root.children.isEmpty()) root = Node(isLeaf = true)
        orphans.forEach { insertEntry(it) }
    }

    private fun collectEntries(node: Node, into: MutableList<Entry<K>>) {
        for (child in node.children) {
            @Suppress("UNCHECKED_CAST")
            if (node.isLeaf) into += child as Entry<K> else collectEntries(child as Node, into)
        }
    }
}
