package com.xiaopiao.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.DirectionsSubway
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector
import com.xiaopiao.app.domain.Category

/** Line icon for a ledger category (emoji only lives on receipt paper and badges). */
fun Category.icon(): ImageVector = when (this) {
    Category.MEAL -> Icons.Outlined.Restaurant
    Category.DRINK -> Icons.Outlined.LocalCafe
    Category.SNACK -> Icons.Outlined.Cookie
    Category.GROCERY -> Icons.Outlined.ShoppingBasket
    Category.TRANSPORT -> Icons.Outlined.DirectionsSubway
    Category.SHOPPING -> Icons.Outlined.ShoppingBag
    Category.FUN -> Icons.Outlined.SportsEsports
    Category.SUBSCRIPTION -> Icons.Outlined.Autorenew
    Category.HOUSING -> Icons.Outlined.Home
    Category.HEALTH -> Icons.Outlined.MedicalServices
    Category.SOCIAL -> Icons.Outlined.CardGiftcard
    Category.LEARNING -> Icons.Outlined.MenuBook
    Category.OTHER -> Icons.Outlined.Receipt
}
