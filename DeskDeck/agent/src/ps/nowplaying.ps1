# DeskDeck now-playing probe.
#
# Prints one line of JSON describing the current Windows media session, or {"playing":false} when
# nothing is active. Windows Runtime calls only answer synchronously through the AsTask bridge below,
# which is why this lives in a script instead of a one-liner: the agent runs it on a timer and reads
# stdout. Any failure prints the same empty object with "error": true so the panel never sees a crash.

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

function Write-Empty([bool]$failed) {
  if ($failed) { '{"playing":false,"error":true}' } else { '{"playing":false}' }
  exit 0
}

try {
  Add-Type -AssemblyName System.Runtime.WindowsRuntime | Out-Null
  $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() |
    Where-Object {
      $_.Name -eq 'AsTask' -and
      $_.GetParameters().Count -eq 1 -and
      $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    })[0]
  if ($null -eq $asTask) { Write-Empty $true }

  function Await($operation, $type) {
    $task = $asTask.MakeGenericMethod($type).Invoke($null, @($operation))
    [void]$task.Wait(3000)
    if (-not $task.IsCompleted) { throw 'timeout' }
    return $task.Result
  }

  $managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
  $manager = Await ($managerType::RequestAsync()) ($managerType)
  $session = $manager.GetCurrentSession()
  if ($null -eq $session) { Write-Empty $false }

  $props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
  $info = $session.GetPlaybackInfo()

  $status = 'paused'
  if ($null -ne $info) {
    switch ([int]$info.PlaybackStatus) {
      4 { $status = 'playing' }
      5 { $status = 'paused' }
      default { $status = 'paused' }
    }
  }

  $app = ''
  if ($session.SourceAppUserModelId) {
    $app = ($session.SourceAppUserModelId -split '!')[-1]
  }

  [pscustomobject]@{
    playing = ($status -eq 'playing')
    status  = $status
    app     = $app
    title   = [string]$props.Title
    artist  = [string]$props.Artist
  } | ConvertTo-Json -Compress
} catch {
  Write-Empty $true
}
