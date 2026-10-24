# FlowTask — smoke E2E contra o stack local (8080 direto).
# Uso: powershell -NoProfile -File scripts\smoke-e2e.ps1
$ErrorActionPreference = 'Stop'

$Base = 'http://localhost:8080/api'
$Sucesso = 0
$Falhas = @()

function Passo($nome, $bloco) {
    try {
        & $bloco
        Write-Host ("PASS   " + $nome)
        $script:Sucesso++
    } catch {
        Write-Host ("FALHA  " + $nome + " -> " + $_.Exception.Message)
        $script:Falhas += $nome
    }
}

function Chamada($metodo, $url, $token, $corpo) {
    $cabecalhos = @{}
    if ($token) { $cabecalhos['Authorization'] = ('Bearer ' + $token) }
    $argsx = @{ Method = $metodo; Uri = ($Base + $url); Headers = $cabecalhos; ContentType = 'application/json'
                 ErrorAction = 'Stop' }
    if ($null -ne $corpo) { $argsx['Body'] = ($corpo | ConvertTo-Json -Depth 6 -Compress) }
    if ($metodo -eq 'DELETE') { Invoke-RestMethod @argsx | Out-Null; return $null }
    return Invoke-RestMethod @argsx
}
function EsperaCodigo($metodo, $url, $token, $corpo, $esperado) {
    try { Chamada $metodo $url $token $corpo | Out-Null; return 0 } catch {
        $codigo = $_.Exception.Response.StatusCode.value__
        if ($codigo -eq $esperado) { return $esperado }
        throw ("esperado $esperado, veio $codigo")
    }
}
function Espera4x($metodo, $url, $token, $corpo) {
    try { Chamada $metodo $url $token $corpo | Out-Null; throw 'chamada devolveu sucesso' } catch {
        $codigo = $_.Exception.Response.StatusCode.value__
        if ($codigo -ge 400) { return $codigo }
        throw
    }
}
function Espera2x($metodo, $url, $token, $corpo) {
    try { Chamada $metodo $url $token $corpo | Out-Null; return $true } catch {
        throw ("esperado sucesso, veio " + $_.Exception.Response.StatusCode.value__)
    }
}

$carimbo = Get-Date -Format 'HHmmss'
$Senha = 'segredotaq1az'

$dono = @{ nome = 'Dono Smoke'; email = "dono.$carimbo@flowtask.dev" }
$parceiro = @{ nome = 'Parceiro Smoke'; email = "parc.$carimbo@flowtask.dev" }

Passo '01 registro do dono' { $script:uD = Chamada 'POST' '/auth/register' $null @{ nome = $dono.nome; email = $dono.email; senha = $Senha } ; if (-not $script:uD.id) { throw 'sem id' } }
Passo '02 registro do parceiro' { $script:uP = Chamada 'POST' '/auth/register' $null @{ nome = $parceiro.nome; email = $parceiro.email; senha = $Senha } ; if (-not $script:uP.id) { throw 'sem id' } }
Passo '03 login do dono' { $script:tD = (Chamada 'POST' '/auth/login' $null @{ email = $dono.email; senha = $Senha }).token ; if (-not $script:tD) { throw 'sem token' } }
Passo '04 login do parceiro' { $script:tP = (Chamada 'POST' '/auth/login' $null @{ email = $parceiro.email; senha = $Senha }).token ; if (-not $script:tP) { throw 'sem token' } }
Passo '05 senha fraca rejeitada (400)' { EsperaCodigo 'POST' '/auth/register' $null @{ nome = 'X'; email = "x.$carimbo@f.dev"; senha = 'curta' } 400 | Out-Null }
Passo '06 dono cria projeto' { $script:p = Chamada 'POST' '/projects' $script:tD @{ nome = 'Launch FlowTask'; descricao = 'smoke'; dataInicio = $null; prazoFinal = $null }; if ($p.papelUsuario -ne 'OWNER') { throw 'papel errado' } }
Passo '07 quadro com 4 colunas padrão' { $script:quadro = Chamada 'GET' ('/projects/{0}/board' -f $p.id) $script:tD $null ; if ($quadro.colunas.Count -lt 4) { throw ('colunas=' + $quadro.colunas.Count) } }
Passo '08 parceiro sem vínculo -> 404 (não revela existência)' { EsperaCodigo 'GET' ('/projects/{0}' -f $p.id) $script:tP $null 404 | Out-Null }
Passo '09 convite para o parceiro' { $script:convite = Chamada 'POST' ('/projects/{0}/invitations' -f $p.id) $script:tD @{ email = $parceiro.email; funcaoProposta = 'MEMBER' }; if ($convite.estado -ne 'PENDENTE') { throw 'estado' } }
Passo '10 parceiro sem vínculo não vê convites do projeto (404)' { EsperaCodigo 'GET' ('/projects/{0}/invitations' -f $p.id) $script:tP $null 404 | Out-Null }
Passo '11 parceiro aceita convite' { $script:aceito = Chamada 'PATCH' ('/invitations/{0}/accept' -f $convite.id) $script:tP $null ; if ($aceito.estado -ne 'ACEITO') { throw 'estado' } }
Passo '12 parceiro agora vê projeto como MEMBER' { $script:visto = Chamada 'GET' ('/projects/{0}' -f $p.id) $script:tP $null ; if ($visto.papelUsuario -ne 'MEMBER') { throw 'papel' } }
$colunas = $quadro.colunas | Sort-Object ordem
$primeiraCol = $colunas[0].id
$terraCol = $colunas[2].id
$conclCol = ($colunas | Where-Object { $_.colunaConclusao }).id
Passo '13 dono cria tarefa' { $script:tarefa = Chamada 'POST' ('/projects/{0}/tasks' -f $p.id) $script:tD @{ titulo = 'Preparar lançamento'; descricao = 'primeira tarefa'; colunaId = $primeiraCol; prioridade = 'ALTA'; responsavelId = $uP.id; prazo = $null } ; if ($tarefa.responsavel.id -ne $uP.id) { throw 'responsável' } }
Passo '14 MEMBER não cria tarefas (403)' { EsperaCodigo 'POST' ('/projects/{0}/tasks' -f $p.id) $script:tP @{ titulo = 'ilegal'; colunaId = $primeiraCol } 403 | Out-Null }
Passo '15 responsável edita textos da própria tarefa' { $e = Chamada 'PUT' ('/tasks/{0}' -f $tarefa.id) $script:tP @{ titulo = 'Preparar o lançamento'; descricao = 'ajustado pelo responsável' } ; if ($e.titulo -notmatch 'o lançamento') { throw 'título' } }
Passo '16 MEMBER não muda prioridade (403)' { EsperaCodigo 'PATCH' ('/tasks/{0}/priority' -f $tarefa.id) $script:tP @{ prioridade = 'CRITICA' } 403 | Out-Null }
Passo '17 MEMBER move a tarefa para revisão' { $m = Chamada 'PATCH' ('/tasks/{0}/move' -f $tarefa.id) $script:tP @{ colunaId = $terraCol; indice = 0 } ; if ($m.colunaId -ne $terraCol) { throw 'coluna' } }
Passo '18 parceiro comenta' { $script:com = Chamada 'POST' ('/tasks/{0}/comments' -f $tarefa.id) $script:tP @{ conteudo = 'QA em cima do lançamento' } ; if (-not $com.id) { throw 'sem id' } }
Passo '19 MEMBER conclui a tarefa movendo para a coluna de conclusão' { $c = Chamada 'PATCH' ('/tasks/{0}/move' -f $tarefa.id) $script:tP @{ colunaId = $conclCol; indice = 0 } ; if (-not $c.concluida) { throw 'deveria concluir' } }
Passo '20 OWNER exclui tarefa com comentário -> 409' { EsperaCodigo 'DELETE' ('/tasks/{0}' -f $tarefa.id) $script:tD $null 409 | Out-Null }
Passo '21 reabre (move de volta)' { $volta = Chamada 'PATCH' ('/tasks/{0}/move' -f $tarefa.id) $script:tP @{ colunaId = $terraCol; indice = 0 } ; if ($volta.concluida) { throw 'deveria reabrir' } }
Passo '22 OWNER promove parceiro a MANAGER' { $script:membros = Chamada 'GET' ('/projects/{0}/members' -f $p.id) $script:tD $null ; $membro = ($membros | Where-Object { $_.usuario.id -eq $uP.id })[0]; Chamada 'PATCH' ('/projects/{0}/members/{1}/role' -f $p.id, $membro.id) $script:tD @{ funcao = 'MANAGER' } | Out-Null }
$membros2 = Chamada 'GET' ('/projects/{0}/members' -f $p.id) $tD $null
$membroParceiro = ($membros2 | Where-Object { $_.usuario.id -eq $uP.id })[0]
Passo '23 OWNER cria a coluna QA' { $script:colunaQa = Chamada 'POST' ('/projects/{0}/columns' -f $p.id) $script:tD @{ nome = 'QA' } ; if (-not $colunaQa.id) { throw 'sem coluna' } }
Passo '24 MANAGER não cria colunas (403 — exige OWNER)' { EsperaCodigo 'POST' ('/projects/{0}/columns' -f $p.id) $script:tP @{ nome = 'Proibida' } 403 | Out-Null }
Passo '25 OWNER remove a coluna vazia (204)' { Espera2x 'DELETE' ('/projects/{0}/columns/{1}' -f $p.id, $colunaQa.id) $script:tD $null | Out-Null }
Passo '26 OWNER cria etiqueta e aplica à tarefa' { $script:et = Chamada 'POST' ('/projects/{0}/labels' -f $p.id) $script:tD @{ nome = 'release'; cor = '#49C6A8' }; Chamada 'PATCH' ('/tasks/{0}/labels' -f $tarefa.id) $script:tD @{ etiquetasIds = @($et.id) } | Out-Null }
Passo '27 OWNER ativa o projeto' { $ativado = Chamada 'PATCH' ('/projects/{0}/status' -f $p.id) $script:tD @{ estado = 'ATIVO' } ; if ($ativado.estado -ne 'ATIVO') { throw 'estado' } }
Passo '28 dashboard do dono responde (projeto ativo contado)' { $dash = Chamada 'GET' '/dashboard/summary?periodo=30' $script:tD $null ; if ($dash.projetosAtivos -lt 1) { throw ('ativos=' + $dash.projetosAtivos) } }
Passo '29 notificações do parceiro não vazias' { $not = Chamada 'GET' '/notifications?size=10' $script:tP $null ; if ($not.total -lt 1) { throw 'sem notificações' } }
Passo '30 histórico por tarefa' { $hist = Chamada 'GET' ('/tasks/{0}/activities?size=20' -f $tarefa.id) $script:tD $null ; if ($hist.total -lt 1) { throw 'sem atividades' } }
Passo '31 OWNER não pode se remover (4xx)' { $euMesmo = ($membros2 | Where-Object { $_.usuario.id -eq $uD.id })[0]; Espera4x 'DELETE' ('/projects/{0}/members/{1}' -f $p.id, $euMesmo.id) $script:tD $null | Out-Null }
Passo '32 membro com tarefa pendente não pode ser removido (409)' { EsperaCodigo 'DELETE' ('/projects/{0}/members/{1}' -f $p.id, $membroParceiro.id) $script:tD $null 409 | Out-Null }
Passo '33 limpa o responsável e o OWNER o remove' { Chamada 'PATCH' ('/tasks/{0}/assignee' -f $tarefa.id) $script:tD @{ responsavelId = $null } | Out-Null; Espera2x 'DELETE' ('/projects/{0}/members/{1}' -f $p.id, $membroParceiro.id) $script:tD $null | Out-Null }

Write-Host ''
Write-Host ('=== SMOKE FINALIZADO: {0} passos ok, {1} falhas ===' -f $Sucesso, $Falhas.Count)
if ($Falhas.Count -gt 0) { $Falhas | ForEach-Object { Write-Host ('  - ' + $_) }; exit 1 }
exit 0
