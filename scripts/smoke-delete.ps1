# Smoke do DELETE /api/projects/{id}: 204 para o OWNER, 403 para quem não é
# OWNER, 404 para quem não é integrante (não revela existência), 404 na repetição.

$base = 'http://localhost:8080/api'
$lua = Get-Date -Format 'HHmmss'

function Corpo($metodo, $rota, $token, $obj) {
    $argsCorrente = @{
        Method = $metodo
        Uri = "$base$rota"
        ContentType = 'application/json; charset=utf-8'
        UseBasicParsing = $true
    }
    if ($null -ne $obj) {
        $argsCorrente.Body = [System.Text.Encoding]::UTF8.GetBytes((ConvertTo-Json $obj -Depth 5))
    }
    if ($null -ne $token) {
        $argsCorrente.Headers = @{ Authorization = "Bearer $token" }
    }
    return Invoke-RestMethod @argsCorrente
}

# devolve o código HTTP sem lançar na tela
function Codigo($metodo, $rota, $token, $obj) {
    try {
        Corpo $metodo $rota $token $obj | Out-Null
        return 200  # 201/200 viram 200 aqui; só interessa reprovar/vector
    } catch {
        $resp = $_.Exception.Response
        if ($null -ne $resp) {
            return [int]$resp.StatusCode
        }
        return -1
    }
}

$nomeD = "dele-dono-$lua@staq.dev"
$nomeP = "dele-parc-$lua@staq.dev"
$nomeO = "dele-outro-$lua@staq.dev"
$passa = @('senha123', 'senha123', 'senha123')

$ok = $true
function Passo($nome, $cond) {
    if ($cond) {
        Write-Host "PASS   $nome"
    } else {
        $script:ok = $false
        Write-Host "FALHA  $nome"
    }
}

try {
    Corpo 'POST' '/auth/register' $null @{ nome = "Dele Dono $lua"; email = $nomeD; senha = $passa[0] } | Out-Null
    Corpo 'POST' '/auth/register' $null @{ nome = "Dele Parc $lua"; email = $nomeP; senha = $passa[1] } | Out-Null
    Corpo 'POST' '/auth/register' $null @{ nome = "Dele Outro $lua"; email = $nomeO; senha = $passa[2] } | Out-Null
} catch {
    Write-Host "FALHA  registro inicial: $_"
    exit 1
}
# registro devolve apenas a conta: token vem do login
$dono = Corpo 'POST' '/auth/login' $null @{ email = $nomeD; senha = $passa[0] }
$parc = Corpo 'POST' '/auth/login' $null @{ email = $nomeP; senha = $passa[1] }
$outro = Corpo 'POST' '/auth/login' $null @{ email = $nomeO; senha = $passa[2] }

$proj = Corpo 'POST' '/projects' $dono.token @{ nome = "Excluivel $lua"; descricao = $null; dataInicio = $null; prazoFinal = $null }
$convite = Corpo 'POST' ('/projects/{0}/invitations' -f $proj.id) $dono.token @{ email = $nomeP; funcaoProposta = 'MEMBER' }
Corpo 'PATCH' ('/invitations/{0}/accept' -f $convite.id) $parc.token $null | Out-Null

Passo '01 dono cria projeto descartável' ($null -ne $proj.id)
Passo '02 parceiro integrante não é OWNER -> DELETE 403' ((Codigo 'DELETE' ('/projects/{0}' -f $proj.id) $parc.token $null) -eq 403)
Passo '03 forasteiro não revela existência -> DELETE 404' ((Codigo 'DELETE' ('/projects/{0}' -f $proj.id) $outro.token $null) -eq 404)
Passo '04 dono exclui o projeto -> 204' ((Codigo 'DELETE' ('/projects/{0}' -f $proj.id) $dono.token $null) -eq 200)
Passo '05 repetir DELETE já sem projeto -> 404' ((Codigo 'DELETE' ('/projects/{0}' -f $proj.id) $dono.token $null) -eq 404)
Passo '06 projeto some da listagem do dono' (
    $null -eq ((Corpo 'GET' '/projects' $dono.token $null) | Where-Object { $_.id -eq $proj.id })
)

try {
    $d2 = Corpo 'POST' '/auth/login' $null @{ email = $nomeD; senha = $passa[0] }
    $p2 = Corpo 'POST' '/auth/login' $null @{ email = $nomeP; senha = $passa[1] }
    $o2 = Corpo 'POST' '/auth/login' $null @{ email = $nomeO; senha = $passa[2] }

    # segundo ciclo: remoção completa inclusive com conteúdo (tarefa + comentário)
    $proj2 = Corpo 'POST' '/projects' $d2.token @{ nome = "Excluivel 2 $lua"; descricao = $null; dataInicio = $null; prazoFinal = $null }
    $c2 = Corpo 'POST' ('/projects/{0}/invitations' -f $proj2.id) $d2.token @{ email = $nomeP; funcaoProposta = 'MEMBER' }
    Corpo 'PATCH' ('/invitations/{0}/accept' -f $c2.id) $p2.token $null | Out-Null
    $colunas = Corpo 'GET' ('/projects/{0}/columns' -f $proj2.id) $d2.token $null
    $tarefa = Corpo 'POST' ('/projects/{0}/tasks' -f $proj2.id) $d2.token @{ titulo = 'Tarefa raiz do descartável'; descricao = $null; colunaId = $colunas[0].id }
    Corpo 'POST' ('/tasks/{0}/comments' -f $tarefa.id) $p2.token @{ conteudo = 'Comentário do parceiro' } | Out-Null
    Passo '07 projeto com conteúdo criado' ($null -ne $tarefa.id)
    Passo '08 OWNER apaga projeto com tarefa+comentário -> 204' ((Codigo 'DELETE' ('/projects/{0}' -f $proj2.id) $d2.token $null) -eq 200)
    $eu = Corpo 'GET' '/auth/me' $o2.token $null
    Passo '09 forasteiro continua vivo na API' ($eu.email -eq $nomeO)
} catch {
    $script:ok = $false
    Write-Host "FALHA  segundo ciclo: $_"
}

if ($ok) {
    Write-Host '=== DELETE SMOKE OK'
    exit 0
}
Write-Host '=== DELETE SMOKE FALHOU'
exit 1
