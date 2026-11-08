package com.example.data

import com.example.R

data class IconStyleItem(val key: String, val label: String, val resId: Int)

object IconStyleUtils {
    val ALL_SHAPES = listOf(
        IconStyleItem("orbit", "Orbit", R.drawable.ic_shape_orbit),
        IconStyleItem("ring", "Rings", R.drawable.ic_shape_ring),
        IconStyleItem("target", "Target", R.drawable.ic_shape_target),
        IconStyleItem("star", "Star", R.drawable.ic_shape_star),
        IconStyleItem("heart", "Heart", R.drawable.ic_shape_heart),
        IconStyleItem("diamond", "Diamond", R.drawable.ic_shape_diamond),
        IconStyleItem("hexagon", "Hexagon", R.drawable.ic_shape_hexagon),
        IconStyleItem("triangle", "Triangle", R.drawable.ic_shape_triangle),
        IconStyleItem("shield", "Shield", R.drawable.ic_shape_shield),
        IconStyleItem("crown", "Crown", R.drawable.ic_shape_crown),
        IconStyleItem("lightning", "Bolt", R.drawable.ic_shape_lightning),
        IconStyleItem("sun", "Sun", R.drawable.ic_shape_sun),
        IconStyleItem("moon", "Crescent", R.drawable.ic_shape_moon),
        IconStyleItem("flame", "Flame", R.drawable.ic_shape_flame),
        IconStyleItem("snowflake", "Snowflake", R.drawable.ic_shape_snowflake),
        IconStyleItem("atom", "Atom", R.drawable.ic_shape_atom),
        IconStyleItem("cube", "Cube", R.drawable.ic_shape_cube),
        IconStyleItem("infinity", "Infinity", R.drawable.ic_shape_infinity),
        IconStyleItem("eye", "Eye", R.drawable.ic_shape_eye),
        IconStyleItem("gear", "Gear", R.drawable.ic_shape_gear),
        IconStyleItem("compass", "Compass", R.drawable.ic_shape_compass),
        IconStyleItem("clover", "Clover", R.drawable.ic_shape_clover),
        IconStyleItem("anchor", "Anchor", R.drawable.ic_shape_anchor),
        IconStyleItem("key", "Key", R.drawable.ic_shape_key),
        IconStyleItem("music", "Music", R.drawable.ic_shape_music),
        IconStyleItem("ghost", "Ghost", R.drawable.ic_shape_ghost),
        IconStyleItem("rocket", "Rocket", R.drawable.ic_shape_rocket),
        IconStyleItem("bell", "Bell", R.drawable.ic_shape_bell),
        IconStyleItem("skull", "Skull", R.drawable.ic_shape_skull),
        IconStyleItem("sparkles", "Sparkles", R.drawable.ic_shape_sparkles),
        IconStyleItem("wave", "Wave", R.drawable.ic_shape_wave),
        IconStyleItem("drop", "Drop", R.drawable.ic_shape_drop),
        IconStyleItem("flower", "Flower", R.drawable.ic_shape_flower)
    )

    fun getIconRes(key: String): Int {
        return ALL_SHAPES.firstOrNull { it.key == key }?.resId ?: R.drawable.ic_shape_orbit
    }
}
