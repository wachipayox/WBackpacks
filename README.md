# WBackpacks

A NeoForge 1.21.1 backpack mod by **Wachipayoxx** focused on one idea: backpack storage should live directly inside the inventory screens you already use.

Instead of opening a dedicated backpack screen, WBackpacks exposes each backpack as a movable desktop-style window layered over normal container screens. Multiple backpack windows can stay open at once, can be moved and closed independently, and remember their layout between sessions.

## Design goals

- No separate backpack GUI.
- Open backpacks from ordinary container screens, including the player inventory and chest-like screens.
- Multiple movable backpack windows with persistent positions and z-order.
- Server-authoritative item movement.
- Configurable backpack capacity for modpacks without deleting overflow contents if capacity is later reduced.
- Backpack windows take input priority over the screen underneath them and are rendered as the top inventory layer, with compatibility hooks for recipe/item viewers such as JEI.

## Current development milestone

The first milestone implements the base backpack, component-backed storage, window manager, persistence, container-screen integration and authoritative slot interactions. Additional backpack types/upgrades are intentionally out of scope until the window interaction is solid.

## Platform

- Minecraft 1.21.1
- NeoForge 21.1.x
- Java 21

## Author

Wachipayoxx
