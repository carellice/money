package com.money.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildFriendly
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.LocalAtm
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalPizza
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PedalBike
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SmokingRooms
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Icon keys stored in the database, mapped to Material symbols. */
object AppIcons {
    val all: Map<String, ImageVector> = linkedMapOf(
        "home" to Icons.Rounded.Home,
        "food" to Icons.Rounded.Restaurant,
        "cart" to Icons.Rounded.ShoppingCart,
        "coffee" to Icons.Rounded.LocalCafe,
        "pizza" to Icons.Rounded.LocalPizza,
        "drink" to Icons.Rounded.LocalBar,
        "car" to Icons.Rounded.DirectionsCar,
        "fuel" to Icons.Rounded.LocalGasStation,
        "bus" to Icons.Rounded.DirectionsBus,
        "train" to Icons.Rounded.Train,
        "bike" to Icons.Rounded.PedalBike,
        "flight" to Icons.Rounded.Flight,
        "hotel" to Icons.Rounded.Hotel,
        "beach" to Icons.Rounded.BeachAccess,
        "repeat" to Icons.Rounded.Autorenew,
        "phone" to Icons.Rounded.PhoneIphone,
        "wifi" to Icons.Rounded.Wifi,
        "bolt" to Icons.Rounded.Bolt,
        "water" to Icons.Rounded.WaterDrop,
        "party" to Icons.Rounded.Celebration,
        "movie" to Icons.Rounded.Movie,
        "music" to Icons.Rounded.MusicNote,
        "game" to Icons.Rounded.SportsEsports,
        "bag" to Icons.Rounded.ShoppingBag,
        "clothes" to Icons.Rounded.Checkroom,
        "devices" to Icons.Rounded.Devices,
        "chair" to Icons.Rounded.Chair,
        "health" to Icons.Rounded.MonitorHeart,
        "hospital" to Icons.Rounded.LocalHospital,
        "fitness" to Icons.Rounded.FitnessCenter,
        "spa" to Icons.Rounded.Spa,
        "school" to Icons.Rounded.School,
        "book" to Icons.AutoMirrored.Rounded.MenuBook,
        "receipt" to Icons.Rounded.Receipt,
        "shield" to Icons.Rounded.Shield,
        "gift" to Icons.Rounded.CardGiftcard,
        "pets" to Icons.Rounded.Pets,
        "baby" to Icons.Rounded.ChildFriendly,
        "smoke" to Icons.Rounded.SmokingRooms,
        "tools" to Icons.Rounded.Build,
        "work" to Icons.Rounded.Work,
        "laptop" to Icons.Rounded.Laptop,
        "undo" to Icons.AutoMirrored.Rounded.Undo,
        "trending" to Icons.AutoMirrored.Rounded.TrendingUp,
        "sell" to Icons.Rounded.Sell,
        "savings" to Icons.Rounded.Savings,
        "card" to Icons.Rounded.CreditCard,
        "atm" to Icons.Rounded.LocalAtm,
        "cash" to Icons.Rounded.Payments,
        "bank" to Icons.Rounded.AccountBalance,
        "wallet" to Icons.Rounded.AccountBalanceWallet,
        "more" to Icons.Rounded.MoreHoriz,
    )

    operator fun get(key: String?): ImageVector = all[key] ?: Icons.Rounded.MoreHoriz
}
