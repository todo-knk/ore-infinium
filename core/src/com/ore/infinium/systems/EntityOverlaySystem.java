package com.ore.infinium.systems;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.managers.TagManager;
import com.badlogic.gdx.math.Vector2;
import com.ore.infinium.Inventory;
import com.ore.infinium.OreWorld;
import com.ore.infinium.components.*;

/**
 * ***************************************************************************
 * Copyright (C) 2015 by Shaun Reich <sreich02@gmail.com>                    *
 * *
 * This program is free software; you can redistribute it and/or            *
 * modify it under the terms of the GNU General Public License as           *
 * published by the Free Software Foundation; either version 2 of           *
 * the License, or (at your option) any later version.                      *
 * *
 * This program is distributed in the hope that it will be useful,          *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of           *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the            *
 * GNU General Public License for more details.                             *
 * *
 * You should have received a copy of the GNU General Public License        *
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.    *
 * ***************************************************************************
 */
@Wire
public class EntityOverlaySystem extends BaseSystem {
    private OreWorld m_world;

    private ComponentMapper<PlayerComponent> playerMapper;
    private ComponentMapper<SpriteComponent> spriteMapper;
    private ComponentMapper<ControllableComponent> controlMapper;
    private ComponentMapper<ItemComponent> itemMapper;
    private ComponentMapper<VelocityComponent> velocityMapper;
    private ComponentMapper<JumpComponent> jumpMapper;
    private ComponentMapper<BlockComponent> blockMapper;
    private ComponentMapper<ToolComponent> toolMapper;

    public EntityOverlaySystem(OreWorld world) {
        m_world = world;
    }

    @Override
    protected void initialize() {
        m_world.m_client.m_hotbarInventory.addListener(new Inventory.SlotListener() {
            @Override
            public void countChanged(byte index, Inventory inventory) {
            }

            @Override
            public void set(byte index, Inventory inventory) {

            }

            @Override
            public void removed(byte index, Inventory inventory) {

            }

            @Override
            public void selected(byte index, Inventory inventory) {
                slotSelected(index, inventory);
            }
        });
    }

    #ERROR

    private void slotSelected(byte index, Inventory inventory) {
        int mainPlayer = getWorld().getSystem(TagManager.class).getEntity(OreWorld.s_mainPlayer).getId();
        PlayerComponent playerComponent = playerMapper.get(mainPlayer);
        int equippedEntity = playerComponent.getEquippedPrimaryItem();

        //if it is here, remove it...we respawn the placement overlay further down either way.
        Entity placementOverlay = getWorld().getSystem(TagManager.class).getEntity(OreWorld.s_itemPlacementOverlay);
        if (placementOverlay != null) {
            getWorld().delete(placementOverlay.getId());
        }

        if (equippedEntity == OreWorld.ENTITY_INVALID) {
            return;
        }

        SpriteComponent crosshairSprite =
                spriteMapper.get(getWorld().getSystem(TagManager.class).getEntity(OreWorld.s_crosshair));
        crosshairSprite.visible = false;

        assert crosshairSprite.noClip;

        if (blockMapper.has(equippedEntity)) {
            // if the switched to item is a block, we should show a crosshair overlay
            crosshairSprite.visible = true;

            //don't show the placement overlay for blocks, just items and other placeable things
            return;
        }

        ToolComponent entityToolComponent = toolMapper.getSafe(equippedEntity);
        if (entityToolComponent != null) {
            if (entityToolComponent.type == ToolComponent.ToolType.Drill) {
                //drill, one of the few cases we want to show the block crosshair...
                crosshairSprite.visible = true;

                //drill has no placement overlay
                //fixme: return;
            }
        }

        //this item is placeable, show an overlay of it so we can see where we're going to place it (by cloning its
        // entity)
        int newPlacementOverlay = m_world.cloneEntity(equippedEntity);
        ItemComponent itemComponent = itemMapper.get(newPlacementOverlay);
        //transition to the in world state, since the cloned source item was in the inventory state, so to would this
        itemComponent.state = ItemComponent.State.InWorldState;

        SpriteComponent spriteComponent = spriteMapper.get(newPlacementOverlay);
        spriteComponent.noClip = true;

        //crosshair shoudln't be visible if the power overlay is
        if (getWorld().getSystem(PowerOverlayRenderSystem.class).overlayVisible) {
            spriteComponent.visible = false;
        }

        getWorld().getSystem(TagManager.class).register(OreWorld.s_itemPlacementOverlay, newPlacementOverlay);

    }

    @Override
    protected void dispose() {
    }

    @Override
    protected void begin() {
    }

    @Override
    protected void processSystem() {
        //        m_batch.setProjectionMatrix(m_world.m_camera.combined);

        Entity entity = getWorld().getSystem(TagManager.class).getEntity(OreWorld.s_itemPlacementOverlay);
        if (entity == null) {
            return;
        }

        int itemPlacementOverlayEntity = entity.getId();

        Vector2 mouse = m_world.mousePositionWorldCoords();
        m_world.alignPositionToBlocks(mouse);

        SpriteComponent spriteComponent = spriteMapper.get(itemPlacementOverlayEntity);
        spriteComponent.sprite.setPosition(mouse.x, mouse.y);
        spriteComponent.placementValid = m_world.isPlacementValid(itemPlacementOverlayEntity);

        //////////////////////ERROR

    }

    @Override
    protected void end() {
    }
}
