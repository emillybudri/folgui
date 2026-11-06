package com.example.folgui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Testes da escolha do movimento entre as telas
class TransicaoTest {

    @Test
    fun `login e cadastro so usam fade`() {
        assertEquals(TipoTransicao.SUAVE, tipoTransicao(Rotas.BOAS_VINDAS, Rotas.INICIO, voltando = false))
        assertEquals(TipoTransicao.SUAVE, tipoTransicao(Rotas.CONFIGURAR, Rotas.APRESENTACAO, voltando = false))
        assertEquals(TipoTransicao.SUAVE, tipoTransicao(Rotas.PERFIL, Rotas.BOAS_VINDAS, voltando = false))
    }

    @Test
    fun `aba segue a ordem das abas nos dois sentidos`() {
        assertEquals(TipoTransicao.ABA, tipoTransicao(Rotas.INICIO, Rotas.EVENTOS, voltando = false))
        assertFalse(andaParaTras(Rotas.INICIO, Rotas.EVENTOS, voltando = false))
        assertTrue(andaParaTras(Rotas.PESSOAS, Rotas.CALENDARIO, voltando = false))
    }

    @Test
    fun `botao voltar entre abas tambem segue a ordem`() {
        assertEquals(TipoTransicao.ABA, tipoTransicao(Rotas.EVENTOS, Rotas.INICIO, voltando = true))
        assertTrue(andaParaTras(Rotas.EVENTOS, Rotas.INICIO, voltando = true))
    }

    @Test
    fun `detalhe anda para o lado e volta`() {
        assertEquals(TipoTransicao.PAGINA, tipoTransicao(Rotas.INICIO, Rotas.DIA, voltando = false))
        assertFalse(andaParaTras(Rotas.INICIO, Rotas.DIA, voltando = false))
        assertTrue(andaParaTras(Rotas.DIA, Rotas.INICIO, voltando = true))
    }

    @Test
    fun `formulario e assistente sobem e descem`() {
        assertEquals(TipoTransicao.FOLHA, tipoTransicao(Rotas.INICIO, Rotas.FORM_EVENTO, voltando = false))
        assertEquals(TipoTransicao.FOLHA, tipoTransicao(Rotas.FORM_EVENTO, Rotas.INICIO, voltando = true))
        assertEquals(TipoTransicao.FOLHA, tipoTransicao(Rotas.EVENTO, Rotas.ASSISTENTE, voltando = false))
    }

    @Test
    fun `sair da folha para outra tela nao fecha a folha`() {
        assertEquals(TipoTransicao.PAGINA, tipoTransicao(Rotas.ASSISTENTE, Rotas.EVENTO, voltando = false))
        assertEquals(TipoTransicao.PAGINA, tipoTransicao(Rotas.EVENTO, Rotas.ASSISTENTE, voltando = true))
    }

    @Test
    fun `ir direto para uma aba parece uma volta`() {
        assertTrue(andaParaTras(Rotas.EVENTO, Rotas.INICIO, voltando = false))
    }
}
