/**
 * Captura de telas do FlowTask via Chrome DevTools Protocol.
 *
 * Uso:  node captura-cdp.mjs [mobile]
 *  - sobe um Chrome headless com porta de depuração,
 *  - faz login na API real (conta demo dono),
 *  - semeia a sessão no navegador e fotografa as rotas principais.
 */

import { spawn } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const PORTA_CDP = 9223;
const ORIGEM = 'http://localhost:4200';
const API = 'http://localhost:8080/api';
const PASTA = 'C:\\Users\\FEBRAS20\\flowtask\\docs\\capturas';

// contas demo (não excluem nada; apenas leem)
const ENTRADA = { email: 'dono.222628@flowtask.dev', senha: 'segredotaq1az' };

const dormir = ms => new Promise(r => setTimeout(r, ms));

/** Espera o endpoint /json/version responder e devolve a URL de depuração do browser. */
async function aguardarChrome() {
  for (let i = 0; i < 60; i += 1) {
    try {
      const r = await fetch(`http://127.0.0.1:${PORTA_CDP}/json/version`);
      if (r.ok) {
        return (await r.json()).webSocketDebuggerUrl;
      }
    } catch {
      // chrome ainda não subiu
    }
    await dormir(250);
  }
  throw new Error('Chrome headless não abriu a porta de depuração.');
}

/** Cria um alvo (aba) novo e devolve seu webSocketDebuggerUrl. */
async function novoAlvo() {
  const r = await fetch(`http://127.0.0.1:${PORTA_CDP}/json/new?url=about:blank`, { method: 'PUT' });
  const alvo = await r.json();
  return alvo.webSocketDebuggerUrl;
}

/** Wrapper mínimo de comandos CDP sobre WebSocket. */
function conectar(url) {
  const ws = new WebSocket(url);
  let seq = 0;
  const pendentes = new Map();
  const eventos = [];
  ws.addEventListener('message', ev => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pendentes.has(msg.id)) {
      const { resolve, reject } = pendentes.get(msg.id);
      pendentes.delete(msg.id);
      if (msg.error) {
        reject(new Error(`${msg.error.message} (${msg.error.data ?? ''})`));
      } else {
        resolve(msg.result);
      }
    } else if (msg.method) {
      eventos.push(msg);
    }
  });
  const pronto = new Promise((resolve, reject) => {
    ws.addEventListener('open', resolve);
    ws.addEventListener('error', reject);
  });
  return {
    pronto,
    enviar(metodo, params = {}) {
      const id = ++seq;
      return new Promise((resolve, reject) => {
        pendentes.set(id, { resolve, reject });
        ws.send(JSON.stringify({ id, method: metodo, params }));
      });
    },
    /** Aguarda pelo próximo evento com o nome dado (com teto de tempo). */
    esperarEvento(metodo, tetoMs = 15_000) {
      const inicio = Date.now();
      return new Promise((resolve, reject) => {
        const sondar = () => {
          const i = eventos.findIndex(e => e.method === metodo);
          if (i >= 0) {
            resolve(eventos.splice(i, 1)[0]);
          } else if (Date.now() - inicio > tetoMs) {
            reject(new Error(`Evento ${metodo} não chegou.`));
          } else {
            setTimeout(sondar, 120);
          }
        };
        sondar();
      });
    },
    fechar() {
      ws.close();
    },
  };
}

async function navegar(cdp, url) {
  const carregou = cdp.esperarEvento('Page.loadEventFired', 25_000);
  await cdp.enviar('Page.navigate', { url });
  await carregou;
  await dormir(2800); // chamadas de API + renderização
}

async function fotografar(cdp, arquivo) {
  const { data } = await cdp.enviar('Page.captureScreenshot', { format: 'png' });
  writeFileSync(arquivo, Buffer.from(data, 'base64'));
  console.log('foto:', arquivo);
}

async function main() {
  const mobile = process.argv.includes('mobile');
  const chrome = spawn(CHROME, [
    '--headless=new',
    `--remote-debugging-port=${PORTA_CDP}`,
    `--user-data-dir=${join(process.env.TEMP ?? 'C:\\Temp', 'flowtask-cdp')}`,
    '--window-size=1440,900',
    '--no-first-run', '--no-default-browser-check', '--disable-extensions',
    'about:blank',
  ], { stdio: 'ignore' });

  try {
    await aguardarChrome();
    const cdp = conectar(await novoAlvo());
    await cdp.pronto;
    await cdp.enviar('Page.enable');
    await cdp.enviar('Runtime.enable');

    // tamanho do dispositivo (desktop 1440x900 / móvel 420x860)
    await cdp.enviar('Emulation.setDeviceMetricsOverride', mobile
      ? { width: 420, height: 860, deviceScaleFactor: 2, mobile: true }
      : { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false });

    // ---- login na API para obter token + usuário + projeto de demonstração
    const respLogin = await fetch(`${API}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(ENTRADA),
    });
    if (!respLogin.ok) {
      throw new Error(`Login demo falhou: ${respLogin.status}`);
    }
    const sessao = await respLogin.json();
    const respProjetos = await fetch(`${API}/projects`, {
      headers: { Authorization: `Bearer ${sessao.token}` },
    });
    const projetos = await respProjetos.json();
    const demo = projetos.find(p => p.nome === 'Launch FlowTask') ?? projetos[0];

    mkdirSync(PASTA, { recursive: true });
    const sufixo = mobile ? '-movel' : '';

    // /entrar ANTES de semear a sessão (com sessão o guarda manda para /projetos)
    await navegar(cdp, `${ORIGEM}/entrar`);
    await fotografar(cdp, join(PASTA, `entrar${sufixo}.png`));

    // ---- semeia a sessão: abre o SPA uma vez, escreve sessionStorage
    await navegar(cdp, `${ORIGEM}/entrar`);
    const semear = `
      sessionStorage.setItem('flowtask.token', ${JSON.stringify(sessao.token)});
      sessionStorage.setItem('flowtask.usuario', ${JSON.stringify(JSON.stringify(sessao.usuario))});
      'semeado'`;
    await cdp.enviar('Runtime.evaluate', { expression: semear });

    // ---- rotas a fotografar (a /entrar sem sessão já foi tirada)
    const idDemo = demo?.id ?? 0;
    const rotas = [
      ['projetos', '/projetos'],
      ['quadro', `/projetos/${idDemo}`],
      ['equipe', `/projetos/${idDemo}/equipe`],
      ['painel', '/painel'],
      ['minhas-tarefas', '/minhas-tarefas'],
      ['notificacoes', '/notificacoes'],
    ];
    for (const [nome, rota] of rotas) {
      await navegar(cdp, `${ORIGEM}${rota}`);
      await fotografar(cdp, join(PASTA, `${nome}${sufixo}.png`));
    }

    // modo modal: fotografa o fluxo de exclusão de projeto (menu -> modal -> nome digitado)
    if (process.argv.includes('modal')) {
      await navegar(cdp, `${ORIGEM}/projetos`);
      const clicarMenu = `
        const b = document.querySelector('button[title="Ações do projeto"]');
        b.click(); 'menu'`;
      await cdp.enviar('Runtime.evaluate', { expression: clicarMenu });
      await dormir(500);
      const excluir = `
        const itens = [...document.querySelectorAll('.card-projeto .menu-ctx button.item-menu')];
        const alvo = itens.find(i => i.textContent.includes('Excluir projeto'));
        alvo.click(); 'aberto'`;
      await cdp.enviar('Runtime.evaluate', { expression: excluir });
      await dormir(500);
      const nome = demo?.nome ?? 'x';
      const digitar = `
        const inp = document.querySelector('input[name="nomeConfirmacao"]');
        const definidor = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
        definidor.call(inp, ${JSON.stringify(nome)});
        inp.dispatchEvent(new Event('input', { bubbles: true }));
        'digitado'`;
      await cdp.enviar('Runtime.evaluate', { expression: digitar });
      await dormir(400);
      await fotografar(cdp, join(PASTA, `modal-exclusao${sufixo}.png`));
    }
    cdp.fechar();
    console.log('concluído.');
  } finally {
    chrome.kill();
  }
}

main().catch(erro => {
  console.error('falhou:', erro.message);
  process.exitCode = 1;
});
