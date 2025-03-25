package com.oussamameg.orbitmenu.listener

/**
 * Interface for listening to orbit menu selection and state change events.
 */
interface OrbitSelectionListener {
    /**
     * Called when a snap animation to a menu item completes.
     *
     * @param itemIndex The index of the menu item that was snapped to (nearest index is selected)
     */
    fun onSnapComplete(itemIndex: Int)

    /**
     * Called when the snap target position is reached during animation.
     *
     * @param reached True if the target position was reached, false otherwise
     */
    fun onSnapTargetReached(reached: Boolean)

    /**
     * Called when the movement state of the orbit menu changes.
     *
     * @param moving True if the menu is currently moving/animating, false when static
     */
    fun onMovementChange(moving: Boolean)
}