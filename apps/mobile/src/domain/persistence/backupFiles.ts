import * as FileSystem from 'expo-file-system/legacy'
import * as DocumentPicker from 'expo-document-picker'
import * as Sharing from 'expo-sharing'

const BACKUP_FILENAME = `learn-code-${new Date().toISOString().slice(0, 10)}.learncode`

export async function createBackupFile(serialized: string): Promise<string> {
  const uri = `${FileSystem.cacheDirectory ?? ''}${BACKUP_FILENAME}`
  await FileSystem.writeAsStringAsync(uri, serialized, { encoding: FileSystem.EncodingType.UTF8 })
  if (await Sharing.isAvailableAsync()) {
    await Sharing.shareAsync(uri, { mimeType: 'application/json', dialogTitle: 'Save your Learn Code backup' })
  }
  return uri
}

export async function restoreBackupFile(): Promise<string> {
  const result = await DocumentPicker.getDocumentAsync({
    type: ['application/json', 'application/octet-stream', '*/*'],
    copyToCacheDirectory: true,
    multiple: false,
  })
  if (result.canceled || !result.assets?.[0]) throw new Error('Backup restore was canceled.')
  const asset = result.assets[0]
  const info = await FileSystem.getInfoAsync(asset.uri)
  if (!info.exists || (info.size ?? 0) > 5 * 1024 * 1024) throw new Error('Backup file is missing or too large.')
  return FileSystem.readAsStringAsync(asset.uri, { encoding: FileSystem.EncodingType.UTF8 })
}
