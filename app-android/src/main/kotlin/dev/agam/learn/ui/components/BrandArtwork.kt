package dev.agam.learn.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import dev.agam.learn.R

enum class BrandAsset(@DrawableRes val resourceId: Int) {
    MascotWave(R.drawable.brand_mascot_wave),
    MascotCelebrate(R.drawable.brand_mascot_celebrate),
    MascotFly(R.drawable.brand_mascot_fly),
    MascotThink(R.drawable.brand_mascot_think),
    ReviewDone(R.drawable.brand_review_done),
    ReviewCards(R.drawable.brand_review_cards),
    StreakFlame(R.drawable.brand_streak_flame),
    XpSpark(R.drawable.brand_xp_spark),
    LessonTrophy(R.drawable.brand_lesson_trophy)
}

@Composable
fun BrandArtwork(
    asset: BrandAsset,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Image(
        painter = painterResource(id = asset.resourceId),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
