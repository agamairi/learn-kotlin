package dev.agam.learn.ui.path

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import dev.agam.learn.di.LocalAppContainer
import dev.agam.learn.ui.components.BrandArtwork
import dev.agam.learn.ui.components.BrandAsset
import dev.agam.learn.ui.experience.LocalReducedMotion
import dev.agam.learn.ui.theme.extendedColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Screen rendering chapters as a winding, tappable trail of circular lesson stones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PathScreen(
    onLessonClick: (lessonId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PathViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = PathViewModel.provideFactory(LocalAppContainer.current)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    val courseProgress by animateFloatAsState(
        targetValue = uiState.overallProgressFraction.coerceIn(0f, 1f),
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 550),
        label = "course-progress"
    )

    LaunchedEffect(Unit) {
        viewModel.loadPath()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Curriculum Path",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item(key = "course-overview") {
                    CourseProgressCard(
                        courseTitle = uiState.courseTitle,
                        completed = uiState.completedLessons,
                        total = uiState.totalLessons,
                        progress = courseProgress
                    )
                }

                itemsIndexed(uiState.chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ChapterProgressCard(
                            number = index + 1,
                            chapterCount = uiState.chapters.size,
                            chapter = chapter
                        )
                        WindingTrail(
                            lessons = chapter.lessons,
                            onLessonClick = { lesson ->
                                if (lesson.status == LessonStatus.LOCKED) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Complete previous lessons to unlock this lesson."
                                        )
                                    }
                                } else {
                                    onLessonClick(lesson.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseProgressCard(
    courseTitle: String,
    completed: Int,
    total: Int,
    progress: Float
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandArtwork(
                    asset = BrandAsset.MascotThink,
                    modifier = Modifier.size(48.dp),
                    contentDescription = null
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "KOTLIN PATH",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = courseTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$completed of $total lessons",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ChapterProgressCard(
    number: Int,
    chapterCount: Int,
    chapter: ChapterUiModel
) {
    val extendedColors = MaterialTheme.extendedColors
    val progress = if (chapter.totalLessonsCount > 0) {
        chapter.completedLessonsCount.toFloat() / chapter.totalLessonsCount
    } else {
        0f
    }
    val reducedMotion = LocalReducedMotion.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 450),
        label = "chapter-progress-$number"
    )
    val isComplete = chapter.totalLessonsCount > 0 &&
        chapter.completedLessonsCount == chapter.totalLessonsCount

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CHAPTER $number / $chapterCount",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${chapter.completedLessonsCount}/${chapter.totalLessonsCount}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isComplete) extendedColors.success else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (chapter.summary.isNotBlank()) {
                Text(
                    text = chapter.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isComplete) extendedColors.success else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun WindingTrail(
    lessons: List<LessonUiModel>,
    onLessonClick: (LessonUiModel) -> Unit
) {
    val nodeCoordinates = remember(lessons.map { it.id }) {
        mutableStateListOf<LayoutCoordinates?>().apply {
            repeat(lessons.size) { add(null) }
        }
    }
    var trailCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val pathColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.84f)
    val trackShadow = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
    val lockedTrack = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { trailCoordinates = it }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val parentCoordinates = trailCoordinates
            if (parentCoordinates != null && lessons.size > 1) {
                val points = nodeCoordinates.map { coordinates ->
                    if (coordinates == null || !coordinates.isAttached) {
                        null
                    } else {
                        parentCoordinates.localPositionOf(
                            sourceCoordinates = coordinates,
                            relativeToSource = Offset(
                                coordinates.size.width / 2f,
                                coordinates.size.height / 2f
                            )
                        )
                    }
                }
                for (index in 0 until points.lastIndex) {
                    val from = points[index] ?: continue
                    val to = points[index + 1] ?: continue
                    val deltaY = (to.y - from.y).coerceAtLeast(0f)
                    val path = Path().apply {
                        moveTo(from.x, from.y)
                        cubicTo(
                            from.x,
                            from.y + deltaY * 0.43f,
                            to.x,
                            to.y - deltaY * 0.43f,
                            to.x,
                            to.y
                        )
                    }
                    drawPath(
                        path = path,
                        color = trackShadow,
                        style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                    )
                    val isLockedSegment = lessons[index + 1].status == LessonStatus.LOCKED
                    drawPath(
                        path = path,
                        color = if (isLockedSegment) lockedTrack else pathColor,
                        style = Stroke(
                            width = if (isLockedSegment) 3.dp.toPx() else 4.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = if (isLockedSegment) {
                                PathEffect.dashPathEffect(
                                    intervals = floatArrayOf(9.dp.toPx(), 9.dp.toPx())
                                )
                            } else {
                                null
                            }
                        )
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            lessons.forEachIndexed { index, lesson ->
                WindingLessonStep(
                    lesson = lesson,
                    index = index,
                    onNodePositioned = { nodeCoordinates[index] = it },
                    onClick = { onLessonClick(lesson) }
                )
            }
        }
    }
}

@Composable
private fun WindingLessonStep(
    lesson: LessonUiModel,
    index: Int,
    onNodePositioned: (LayoutCoordinates) -> Unit,
    onClick: () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    var arrivalFinished by remember(lesson.id, reducedMotion) {
        mutableStateOf(reducedMotion)
    }
    LaunchedEffect(lesson.id, reducedMotion) {
        if (!reducedMotion) {
            delay((index % 6) * 32L)
            arrivalFinished = true
        }
    }
    val arrivalScale by animateFloatAsState(
        targetValue = if (arrivalFinished) 1f else 0.94f,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 220),
        label = "lesson-arrival-scale-${lesson.id}"
    )
    val arrivalOffset by animateDpAsState(
        targetValue = if (arrivalFinished) 0.dp else 7.dp,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 220),
        label = "lesson-arrival-offset-${lesson.id}"
    )
    val interactionSource = remember(lesson.id) { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (!reducedMotion && isPressed) 0.95f else 1f,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 100),
        label = "lesson-press-scale-${lesson.id}"
    )
    val pressOffset by animateDpAsState(
        targetValue = if (!reducedMotion && isPressed) 3.dp else 0.dp,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 100),
        label = "lesson-press-offset-${lesson.id}"
    )
    val isCompleted = lesson.status == LessonStatus.COMPLETED
    val isUnlocked = lesson.status == LessonStatus.UNLOCKED
    val faceColor = when {
        isCompleted || isUnlocked -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val faceContentColor = when {
        isCompleted || isUnlocked -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bevelColor = if (isCompleted || isUnlocked) {
        darken(MaterialTheme.colorScheme.primary, 0.84f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val labelSurface = MaterialTheme.colorScheme.surface
    val statusLabel = when {
        isCompleted -> "Completed"
        isUnlocked -> "Ready"
        else -> "Locked"
    }
    val accessibleLabel = "$statusLabel: ${lesson.title}, ${lesson.exerciseCount} exercises"
    val labelWidthFraction = 0.48f
    val laneFraction = laneFraction(index)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 144.dp)
    ) {
        val labelWidth = minOf(148.dp, maxWidth * labelWidthFraction)
        val horizontalShift = (maxWidth * (laneFraction - 0.5f))
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = horizontalShift)
                .width(labelWidth),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(68.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    modifier = Modifier
                        .offset(y = 3.dp)
                        .size(64.dp),
                    shape = CircleShape,
                    color = bevelColor
                ) {}
                Surface(
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer {
                            val scale = arrivalScale * pressScale
                            scaleX = scale
                            scaleY = scale
                            translationY = arrivalOffset.toPx() + pressOffset.toPx()
                        }
                        .onGloballyPositioned(onNodePositioned)
                        .semantics {
                            contentDescription = accessibleLabel
                            role = Role.Button
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            role = Role.Button,
                            onClick = onClick
                        ),
                    shape = CircleShape,
                    color = faceColor,
                    contentColor = faceContentColor,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isUnlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when {
                            isCompleted -> Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(35.dp)
                            )
                            isUnlocked -> Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp)
                            )
                            else -> Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(29.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(5.dp))
            SpeechBubbleTail(color = labelSurface)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = labelSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = lesson.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "$statusLabel · ${lesson.exerciseCount} exercises",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeechBubbleTail(color: Color) {
    Canvas(modifier = Modifier.size(width = 13.dp, height = 7.dp)) {
        val tail = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(0f, size.height)
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path = tail, color = color)
    }
}

private fun laneFraction(index: Int): Float = when (index % 6) {
    0, 3 -> 0.5f
    1, 4 -> 0.25f
    else -> 0.75f
}

private fun darken(color: Color, factor: Float): Color = Color(
    red = color.red * factor,
    green = color.green * factor,
    blue = color.blue * factor,
    alpha = color.alpha
)
