$ErrorActionPreference = "Stop"
$docx = "D:\Ai\opencode-projects\prolean\docs\ProLean_Technical_Design_Document.docx"
$pdf = "D:\Ai\opencode-projects\prolean\docs\ProLean_Technical_Design_Document.pdf"

$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
try {
    $doc = $word.Documents.Open($docx, $false, $false)
    for ($i = 0; $i -lt 2; $i++) {
        $doc.Repaginate()
        $doc.Fields.Update() | Out-Null
        if ($doc.TablesOfContents.Count -gt 0) {
            $doc.TablesOfContents.Item(1).Update()
        }
    }
    $doc.Repaginate()
    if ($doc.TablesOfContents.Count -gt 0) {
        $doc.TablesOfContents.Item(1).Update()
    }
    $doc.Repaginate()
    $doc.Save()
    $doc.ExportAsFixedFormat($pdf, 17)
    $pages = $doc.ComputeStatistics(2)
    $tocCount = $doc.TablesOfContents.Count
    Write-Output "TOC_UPDATED tablesOfContents=$tocCount pages=$pages"
    $doc.Close($false)
} finally {
    $word.Quit()
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null
}
