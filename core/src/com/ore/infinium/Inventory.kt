/**
MIT License

Copyright (c) 2016 Shaun Reich <sreich02@gmail.com>

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

package com.ore.infinium

import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.ore.infinium.components.*

/**
 * @param _owningPlayer
 * *         entity id of player who owns this inventory
 * //fixme unneeded??
 */
@Wire
class Inventory
(var owningPlayer: Int, val inventoryType: InventoryType) {
    //selection is hotbar only
    var selectedSlot: Int = 0
    var previousSelectedSlot: Int = 0

    internal var m_listeners = mutableListOf<SlotListener>()

    private lateinit var itemMapper: ComponentMapper<ItemComponent>
    private lateinit var toolMapper: ComponentMapper<ToolComponent>
    private lateinit var blockMapper: ComponentMapper<BlockComponent>
    private lateinit var floraMapper: ComponentMapper<FloraComponent>
    private lateinit var powerConsumerMapper: ComponentMapper<PowerConsumerComponent>
    private lateinit var powerDeviceMapper: ComponentMapper<PowerDeviceComponent>
    private lateinit var powerGeneratorMapper: ComponentMapper<PowerGeneratorComponent>

    private val m_slots: Array<Int?>

    init {

        if (inventoryType == InventoryType.Hotbar) {
            m_slots = arrayOfNulls(maxHotbarSlots)
        } else {
            //m_slots = IntArray(maxSlots)

            m_slots = arrayOfNulls(maxSlots);
        }
    }

    fun addListener(listener: SlotListener) {
        m_listeners.add(listener)
    }

    fun setCount(index: Int, newCount: Int) {
        val item = m_slots[index]
        if (item != null) {
            itemMapper.get(item).stackSize = newCount

            m_listeners.forEach { it.countChanged(index, this) }
        }
    }

    fun selectSlot(index: Int) {
        previousSelectedSlot = selectedSlot
        selectedSlot = index

        m_listeners.forEach { it.selected(index, this) }
    }

    /**
     * Finds the next available free slot in this inventory.
     * It'll search through this inventory and determine if
     * the items are the same and try to merge (combine it with it)
     * if that's successful, returns TypeOfAdd.Merged. If this is the
     * result, then caller must delete the old item (one that got merged)
     * themselves.
     *
     * If no merge can happen, it will find a free slot to place it in.
     * (return TypeOfAdd.Inserted)
     *
     * If there is no free slots, it will fail (return TypeOfAdd.Failed)
     *
     * Also handles reassociating the item component's inventory association.
     * (which inventory it is in, which index) and which type
     */
    fun placeItemInNextFreeSlot(itemEntityId: Int): ItemAddResult {
        val slotIndexToMerge = m_slots.filterNotNull().indexOfFirst { itemInSlotId ->
            canCombineItems(itemEntityId, itemInSlotId)
        }

        val result: ItemAddResult
        if (slotIndexToMerge == -1) {
            //merge not possible/failed. no like items. place it in a different slot

            val slotIndexToInsert = m_slots.indexOfFirst { it -> it != null }
            if (slotIndexToInsert == -1) {
                //no free places at all! placement failed.
                result = ItemAddResult(resultType = ItemAddResult.TypeOfAdd.Failed)
            } else {
                //found a free spot, use this one!
                result = ItemAddResult(resultType = ItemAddResult.TypeOfAdd.Inserted)
                assignItemIdToInventorySlot(itemEntityId, slotIndexToInsert)
            }

        } else {
            val mergedItemId = m_slots[slotIndexToMerge]!!
            //merge us into this one
            result = ItemAddResult(resultType = ItemAddResult.TypeOfAdd.Merged)//, mergedEntityId = mergedItemId)
            mergeItemIntoSecond(itemIdToObsolete = 0, itemIdToMerge = mergedItemId)
        }

        return result
    }

    private fun assignItemIdToInventorySlot(itemEntityId: Int, slotIndexToInsert: Int) {
        itemMapper.get(itemEntityId).apply {
            state = ItemComponent.State.InInventoryState
            inventoryIndex = slotIndexToInsert
        }
    }

    /**
     * merges item into the other one, to combine them (like when
     * they get picked up and are the same item)
     *
     * @param itemIdToObsolete item that will be defunct now because it got
     * merged into the other one.
     *
     * @param itemIdToMerge the itemid that will remain, that the other one gets
     * merged with.
     */
    private fun mergeItemIntoSecond(itemIdToObsolete: Int, itemIdToMerge: Int) {
        val itemToObsoleteComp = itemMapper.get(itemIdToObsolete)

        val itemToMergeComp = itemMapper.get(itemIdToMerge).apply {
            //merge in the other one, combining items but don't exceed the max these types of items can hold
            stackSize = (this.stackSize + itemToObsoleteComp.stackSize).coerceAtMost(this.maxStackSize)
            //all the other state is fine, because it's already been and still remains in the same spot.
            //just count changes
        }

        itemToObsoleteComp.apply {
            stackSize = -1
            inventoryIndex = -1
        }
    }

    private fun canCombineItems(itemId: Int, itemInSlotId: Int): Boolean {
        val itemComp1 = itemMapper.get(itemId)
        val itemComp2 = itemMapper.get(itemInSlotId)
        if (!itemComp1.canCombineWith(itemComp2)) {
            return false
        }

        if (toolMapper.has(itemId)) {
            val comp1 = toolMapper.get(itemId)
            val comp2 = toolMapper.get(itemInSlotId)
            if (!comp1.canCombineWith(comp2)) {
                return false
            }
        }

        if (powerDeviceMapper.has(itemId)) {
            val comp1 = powerDeviceMapper.get(itemId)
            val comp2 = powerDeviceMapper.get(itemInSlotId)
            if (!comp1.canCombineWith(comp2)) {
                return false
            }
        }

        if (powerConsumerMapper.has(itemId)) {
            val comp1 = powerConsumerMapper.get(itemId)
            val comp2 = powerConsumerMapper.get(itemInSlotId)
            if (!comp1.canCombineWith(comp2)) {
                return false
            }
        }

        if (powerGeneratorMapper.has(itemId)) {
            val comp1 = powerGeneratorMapper.get(itemId)
            val comp2 = powerGeneratorMapper.get(itemInSlotId)
            if (!comp1.canCombineWith(comp2)) {
                return false
            }
        }

        if (blockMapper.has(itemId)) {
            val comp1 = blockMapper.get(itemId)
            val comp2 = blockMapper.get(itemInSlotId)
            if (!comp1.canCombineWith(comp2)) {
                return false
            }
        }

        if (floraMapper.has(itemId)) {
            val comp1 = floraMapper.get(itemId)
            val comp2 = floraMapper.get(itemInSlotId)
            if (!comp1.canCombineWith(comp2)) {
                return false
            }
        }

        return true
    }

    /**
     * replaces the slot at @param index with @param entity id

     * @param index
     * *
     * @param entity
     */
    fun setSlot(index: Int, entity: Int) {
        m_slots[index] = entity

        m_listeners.forEach { it.set(index, this) }
    }

    /**
     * @param index
     * *
     * *
     * @return entity id of the item taken
     */
    fun takeItem(index: Int): Int? {
        val tmpItem = m_slots[index]

        if (tmpItem != null) {
            m_slots[index] = null

            m_listeners.forEach { it.removed(index, this) }
        }

        return tmpItem
    }

    /**
     * @param index
     * *
     * *
     * @return entity id at index
     */
    fun itemEntity(index: Int): Int? {
        return m_slots[index]
    }

    enum class InventoryType {
        Hotbar,
        Inventory //standard inventory
    }

    interface SlotListener {
        open fun countChanged(index: Int, inventory: Inventory) {
        }

        open fun set(index: Int, inventory: Inventory) {
        }

        open fun removed(index: Int, inventory: Inventory) {
        }

        open fun selected(index: Int, inventory: Inventory) {
        }
    }

    companion object {
        val maxHotbarSlots: Int = 8
        val maxSlots: Int = 32
    }

}

class ItemAddResult(val resultType: ItemAddResult.TypeOfAdd
        //the entityid we merged with. you should delete the old one, or
        //you'll have 2 items
        /*
        i thought about this, but don't think i need this even.
        these methods are mostly fire and forget, i don't believe we
        ever need to know which entity id it ended up combining with??
        could be wrong. but i think networking can just change count and delete
        old when it knows it is merged
        val mergedEntityId: Int = -1*/) {

    enum class TypeOfAdd {
        //merged with existing item
        Merged,
        //no free spots
        Failed,
        //found an empty spot and used that.
        Inserted
    }
}

