# DeskDeck input helper.
#
# The agent starts this once and writes one JSON command per line to stdin:
#   { "id": 7, "cmd": { "kind": "media", "action": "next" } }
# and reads one JSON reply per line on stdout:
#   { "id": 7, "ok": true }        or        { "id": 7, "ok": false, "error": "unknown command" }
#
# Phase 3 ships this as a script instead of WinHelper.exe: it needs no toolchain, and the allowlist
# below is the security boundary either way. Anything not listed is rejected and logged, never
# passed to a shell, and no field of a command is ever concatenated into a script.

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

public static class DeskDeckKeys
{
    [DllImport("user32.dll", SetLastError = true)]
    private static extern void keybd_event(byte virtualKey, byte scanCode, uint flags, UIntPtr extraInfo);

    public static void Down(byte virtualKey) { keybd_event(virtualKey, 0, 0, UIntPtr.Zero); }
    public static void Up(byte virtualKey) { keybd_event(virtualKey, 0, 2, UIntPtr.Zero); }

    // Down then up: what a real key press looks like to the media and volume handlers.
    public static void Press(byte virtualKey)
    {
        keybd_event(virtualKey, 0, 0, UIntPtr.Zero);
        keybd_event(virtualKey, 0, 2, UIntPtr.Zero);
    }
}
'@

$VirtualKeys = @{
    'media.playpause' = 0xB3
    'media.next'      = 0xB0
    'media.prev'      = 0xB1
    'media.stop'      = 0xB2
    'mute'            = 0xAD
    'volume.up'       = 0xAF
    'volume.down'     = 0xAE
    'ctrl'            = 0x11
    'shift'           = 0x10
    'alt'             = 0x12
    'win'             = 0x5B
    'enter'           = 0x0D
    'esc'             = 0x1B
    'tab'             = 0x09
    'space'           = 0x20
    'backspace'       = 0x08
    'delete'          = 0x2E
    'insert'          = 0x2D
    'home'            = 0x24
    'end'             = 0x23
    'pageup'          = 0x21
    'pagedown'        = 0x22
    'up'              = 0x26
    'down'            = 0x28
    'left'            = 0x25
    'right'           = 0x27
    'printscreen'     = 0x2C
}

# Letters, digits and F1-F12 are computed; every other name must appear in the table above.
function Get-VirtualKey([string]$name) {
    if ($VirtualKeys.ContainsKey($name)) { return [byte]$VirtualKeys[$name] }
    if ($name -cmatch '^[a-z]$') { return [byte](0x41 + ([int][char]$name - [int][char]'a')) }
    if ($name -cmatch '^[0-9]$') { return [byte](0x30 + [int]$name) }
    if ($name -cmatch '^f([1-9]|1[0-2])$') { return [byte](0x70 + [int]$name.Substring(1) - 1) }
    throw "unknown key: $name"
}

function Reply($id, $ok, $error) {
    $message = @{ id = $id; ok = $ok }
    if ($error) { $message.error = [string]$error }
    [Console]::Out.WriteLine(($message | ConvertTo-Json -Compress))
    [Console]::Out.Flush()
}

while ($null -ne ($line = [Console]::In.ReadLine())) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }

    $id = $null
    try {
        $message = $line | ConvertFrom-Json
        $id = $message.id
        $cmd = $message.cmd
        if ($null -eq $cmd -or $null -eq $cmd.kind) { throw 'missing cmd' }

        switch ([string]$cmd.kind) {
            'media' {
                $action = [string]$cmd.action
                if ($action -notin @('playpause', 'next', 'prev', 'stop')) { throw "unknown media action: $action" }
                [DeskDeckKeys]::Press([byte]$VirtualKeys["media.$action"])
            }
            'volume' {
                $delta = [int]$cmd.delta
                if ($delta -eq 0 -or [Math]::Abs($delta) -gt 10) { throw 'volume delta out of range' }
                $key = if ($delta -gt 0) { $VirtualKeys['volume.up'] } else { $VirtualKeys['volume.down'] }
                for ($i = 0; $i -lt [Math]::Abs($delta); $i++) { [DeskDeckKeys]::Press([byte]$key) }
            }
            'mute' {
                [DeskDeckKeys]::Press([byte]$VirtualKeys['mute'])
            }
            'lock' {
                # Same call the Win+L shortcut makes; it never touches data.
                Start-Process -FilePath 'rundll32.exe' -ArgumentList 'user32.dll,LockWorkStation' -WindowStyle Hidden
            }
            'sleep' {
                Start-Process -FilePath 'rundll32.exe' -ArgumentList 'powrprof.dll,SetSuspendState 0,1,0' -WindowStyle Hidden
            }
            'launch' {
                # The agent validated this too; the same rule is enforced here so a bad deck entry
                # cannot turn into an arbitrary program start.
                $target = [string]$cmd.target
                $isUrl = $target.StartsWith('http://') -or $target.StartsWith('https://')
                if (-not $isUrl -and -not [System.IO.Path]::IsPathRooted($target)) {
                    throw 'launch target must be an http(s) URL or an absolute path'
                }
                Start-Process -FilePath $target
            }
            'keys' {
                # One real key plus at most three modifiers, pressed in order and released in reverse.
                $names = @($cmd.keys)
                if ($names.Count -eq 0 -or $names.Count -gt 4) { throw 'key sequence out of range' }
                $codes = @()
                foreach ($name in $names) { $codes += (Get-VirtualKey ([string]$name)) }
                $main = [byte]$codes[$codes.Count - 1]
                $modifiers = @(0x11, 0x10, 0x12, 0x5B)
                $held = @()
                for ($i = 0; $i -lt $codes.Count - 1; $i++) {
                    $code = [byte]$codes[$i]
                    if ($modifiers -notcontains [int]$code) { throw 'modifiers must come first' }
                    if ($held -contains $code) { throw 'duplicate modifier' }
                    [DeskDeckKeys]::Down($code)
                    $held += $code
                }
                [DeskDeckKeys]::Press($main)
                for ($i = $held.Count - 1; $i -ge 0; $i--) { [DeskDeckKeys]::Up([byte]$held[$i]) }
            }
            default { throw "unknown command: $($cmd.kind)" }
        }
        Reply $id $true $null
    }
    catch {
        [Console]::Error.WriteLine("rejected: $($_.Exception.Message)")
        Reply $id $false $_.Exception.Message
    }
}
