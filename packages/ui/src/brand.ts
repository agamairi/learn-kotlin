export type BrandAssetName =
  | "mascotWave"
  | "mascotCelebrate"
  | "mascotFly"
  | "mascotThink"
  | "reviewDone"
  | "reviewCards"
  | "streakFlame"
  | "xpSpark"
  | "lessonTrophy"

export type BrandAssetDefinition = {
  name: BrandAssetName
  sourceFile: string
  contentDescription: string
}

export const brandAssets: Record<BrandAssetName, BrandAssetDefinition> = {
  mascotWave: {
    name: "mascotWave",
    sourceFile: "assets/brand/mascot_wave.svg",
    contentDescription: "Koda waving",
  },
  mascotCelebrate: {
    name: "mascotCelebrate",
    sourceFile: "assets/brand/mascot_celebrate.svg",
    contentDescription: "Koda celebrating",
  },
  mascotFly: {
    name: "mascotFly",
    sourceFile: "assets/brand/mascot_fly.svg",
    contentDescription: "Koda flying",
  },
  mascotThink: {
    name: "mascotThink",
    sourceFile: "assets/brand/mascot_think.svg",
    contentDescription: "Koda thinking",
  },
  reviewDone: {
    name: "reviewDone",
    sourceFile: "assets/brand/review_done.svg",
    contentDescription: "Review complete",
  },
  reviewCards: {
    name: "reviewCards",
    sourceFile: "assets/brand/review_cards.svg",
    contentDescription: "Review cards",
  },
  streakFlame: {
    name: "streakFlame",
    sourceFile: "assets/brand/streak_flame.svg",
    contentDescription: "Streak flame",
  },
  xpSpark: {
    name: "xpSpark",
    sourceFile: "assets/brand/xp_spark.svg",
    contentDescription: "XP spark",
  },
  lessonTrophy: {
    name: "lessonTrophy",
    sourceFile: "assets/brand/lesson_trophy.svg",
    contentDescription: "Lesson trophy",
  },
}
