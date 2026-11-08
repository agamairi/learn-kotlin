const { getDefaultConfig } = require('expo/metro-config')

const config = getDefaultConfig(__dirname)

const { transformer, resolver } = config

config.transformer = {
  ...transformer,
  babelTransformerPath: require.resolve('react-native-svg-transformer/expo'),
}

config.resolver = {
  ...resolver,
  assetExts: resolver.assetExts.filter((ext) => ext !== 'svg'),
  sourceExts: [...resolver.sourceExts, 'svg'],
}

// expo-sqlite's web preview worker imports its bundled wa-sqlite module as a
// WebAssembly asset. Metro does not include .wasm in the default asset list.
if (!config.resolver.assetExts.includes('wasm')) {
  config.resolver.assetExts.push('wasm')
}

module.exports = config
