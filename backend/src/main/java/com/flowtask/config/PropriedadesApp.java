package com.flowtask.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades da aplicação sob o prefixo {@code app.*}.
 */
@ConfigurationProperties(prefix = "app")
public class PropriedadesApp {

    private Seguranca seguranca = new Seguranca();
    private Convites convites = new Convites();
    private Email email = new Email();

    public Seguranca getSeguranca() {
        return seguranca;
    }

    public void setSeguranca(Seguranca seguranca) {
        this.seguranca = seguranca;
    }

    public Convites getConvites() {
        return convites;
    }

    public void setConvites(Convites convites) {
        this.convites = convites;
    }

    public Email getEmail() {
        return email;
    }

    public void setEmail(Email email) {
        this.email = email;
    }

    public static class Seguranca {
        private Jwt jwt = new Jwt();
        private Cors cors = new Cors();
        private Senhas senhas = new Senhas();

        public Jwt getJwt() {
            return jwt;
        }

        public void setJwt(Jwt jwt) {
            this.jwt = jwt;
        }

        public Cors getCors() {
            return cors;
        }

        public void setCors(Cors cors) {
            this.cors = cors;
        }

        public Senhas getSenhas() {
            return senhas;
        }

        public void setSenhas(Senhas senhas) {
            this.senhas = senhas;
        }

        public static class Jwt {
            private String segredo;
            private long expiracaoSegundos = 28800;

            public String getSegredo() {
                return segredo;
            }

            public void setSegredo(String segredo) {
                this.segredo = segredo;
            }

            public long getExpiracaoSegundos() {
                return expiracaoSegundos;
            }

            public void setExpiracaoSegundos(long expiracaoSegundos) {
                this.expiracaoSegundos = expiracaoSegundos;
            }
        }

        public static class Cors {
            private String origens = "http://localhost:4200";

            public String getOrigens() {
                return origens;
            }

            public void setOrigens(String origens) {
                this.origens = origens;
            }
        }

        public static class Senhas {
            private String padrao;
            private String mensagem;

            public String getPadrao() {
                return padrao;
            }

            public void setPadrao(String padrao) {
                this.padrao = padrao;
            }

            public String getMensagem() {
                return mensagem;
            }

            public void setMensagem(String mensagem) {
                this.mensagem = mensagem;
            }
        }
    }

    public static class Convites {
        private int expiracaoDias = 7;

        public int getExpiracaoDias() {
            return expiracaoDias;
        }

        public void setExpiracaoDias(int expiracaoDias) {
            this.expiracaoDias = expiracaoDias;
        }
    }

    public static class Email {
        private boolean habilitado = false;
        private String remetente;

        public boolean isHabilitado() {
            return habilitado;
        }

        public void setHabilitado(boolean habilitado) {
            this.habilitado = habilitado;
        }

        public String getRemetente() {
            return remetente;
        }

        public void setRemetente(String remetente) {
            this.remetente = remetente;
        }
    }
}
