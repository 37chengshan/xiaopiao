package com.xiaopiao.app.ui.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.DirectionsSubway
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsBar
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The ~30 Material symbols a category can wear (tt#71). Keys are stable strings stored in Room, so the
 * icon set can grow without touching the database. [defaultKey] is what a new custom category starts on.
 */
object CategoryIconCatalog {
    data class Entry(val key: String, val icon: ImageVector)

    val entries: List<Entry> = listOf(
        Entry("restaurant", Icons.Outlined.Restaurant),
        Entry("local_cafe", Icons.Outlined.LocalCafe),
        Entry("cookie", Icons.Outlined.Cookie),
        Entry("shopping_basket", Icons.Outlined.ShoppingBasket),
        Entry("directions_subway", Icons.Outlined.DirectionsSubway),
        Entry("shopping_bag", Icons.Outlined.ShoppingBag),
        Entry("sports_esports", Icons.Outlined.SportsEsports),
        Entry("autorenew", Icons.Outlined.Autorenew),
        Entry("home", Icons.Outlined.Home),
        Entry("medical_services", Icons.Outlined.MedicalServices),
        Entry("card_giftcard", Icons.Outlined.CardGiftcard),
        Entry("menu_book", Icons.Outlined.MenuBook),
        Entry("receipt", Icons.Outlined.Receipt),
        Entry("pets", Icons.Outlined.Pets),
        Entry("fitness_center", Icons.Outlined.FitnessCenter),
        Entry("local_gas_station", Icons.Outlined.LocalGasStation),
        Entry("flight", Icons.Outlined.Flight),
        Entry("hotel", Icons.Outlined.Hotel),
        Entry("sports_bar", Icons.Outlined.SportsBar),
        Entry("cake", Icons.Outlined.Cake),
        Entry("celebration", Icons.Outlined.Celebration),
        Entry("child_care", Icons.Outlined.ChildCare),
        Entry("work", Icons.Outlined.Work),
        Entry("savings", Icons.Outlined.Savings),
        Entry("medication", Icons.Outlined.Medication),
        Entry("spa", Icons.Outlined.Spa),
        Entry("sports_soccer", Icons.Outlined.SportsSoccer),
        Entry("local_florist", Icons.Outlined.LocalFlorist),
        Entry("phone_iphone", Icons.Outlined.PhoneIphone),
        Entry("local_laundry_service", Icons.Outlined.LocalLaundryService),
    )

    const val defaultKey: String = "cake"

    /** Unknown keys fall back to a neutral glyph instead of crashing on a future/removed icon. */
    fun icon(key: String): ImageVector =
        entries.firstOrNull { it.key == key }?.icon ?: Icons.Outlined.AutoAwesome
}
