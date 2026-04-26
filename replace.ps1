$files = Get-ChildItem -Path ".\src\main\java\com\lms\www\leadmanagement" -Recurse -Filter "*.java"
foreach ($file in $files) {
    if ($file.FullName -match "Lead\.java$") { continue } # Skip Lead.java as we already modified it perfectly
    $content = Get-Content -Raw $file.FullName
    
    $originalContent = $content

    $content = [System.Text.RegularExpressions.Regex]::Replace($content, "Lead\.Status\.valueOf\((.+?)\)", '$1')
    $content = [System.Text.RegularExpressions.Regex]::Replace($content, "com\.lms\.www\.leadmanagement\.entity\.Lead`$Status\.([A-Z0-9_]+)", '"$1"')
    $content = [System.Text.RegularExpressions.Regex]::Replace($content, "Lead\.Status\.([A-Z0-9_]+)", '"$1"')
    $content = [System.Text.RegularExpressions.Regex]::Replace($content, "Lead\.Status", "String")

    if ($originalContent -ne $content) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
        Write-Host "Modified $($file.Name)"
    }
}
Write-Host "Replacement Complete"
