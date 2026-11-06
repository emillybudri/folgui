package com.example.folgui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ============================================================================
// Tema Visual (Material Design 3) com modo claro e escuro
// ============================================================================
object Espaco {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 40.dp
    val alvoToque = 48.dp

    /** Espaço no fim das listas para o FAB não cobrir o último item. */
    val folgaFab = 96.dp

    /** Largura máxima do conteúdo em telas grandes (tablet, paisagem). */
    val larguraMaxima = 600.dp
}

// Violeta é sempre ação ou evento. Ele nunca significa um estado do dia, e os estados
// (folga, trabalho, férias, sono) têm cada um a sua cor, definida em Tom logo abaixo.
private val CoresClaras = lightColorScheme(
    primary = Color(0xFF6246EA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFECE7FF),
    onPrimaryContainer = Color(0xFF3A1FB8),
    secondary = Color(0xFF6246EA),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECE7FF),
    onSecondaryContainer = Color(0xFF3A1FB8),
    tertiary = Color(0xFF1F78D6),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD9ECFF),
    onTertiaryContainer = Color(0xFF0B4A80),
    background = Color(0xFFFAF9F7),
    onBackground = Color(0xFF1C1B22),
    surface = Color(0xFFFAF9F7),
    onSurface = Color(0xFF1C1B22),
    surfaceVariant = Color(0xFFEDEBE7),
    onSurfaceVariant = Color(0xFF6B6875),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F4F1),
    surfaceContainer = Color(0xFFF1EFEB),
    surfaceContainerHigh = Color(0xFFECEAE6),
    surfaceContainerHighest = Color(0xFFE5E3DF),
    outline = Color(0xFF8E8B97),
    outlineVariant = Color(0xFFE7E5E1),
    error = Color(0xFFC9301C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE0DA),
    onErrorContainer = Color(0xFF8C2412),
    inverseSurface = Color(0xFF2C2B32),
    inverseOnSurface = Color(0xFFF2F1F5),
    inversePrimary = Color(0xFFA897FF)
)

private val CoresEscuras = darkColorScheme(
    primary = Color(0xFFA897FF),
    onPrimary = Color(0xFF1E1458),
    primaryContainer = Color(0xFF2A2360),
    onPrimaryContainer = Color(0xFFD9D1FF),
    secondary = Color(0xFFA897FF),
    onSecondary = Color(0xFF1E1458),
    secondaryContainer = Color(0xFF2A2360),
    onSecondaryContainer = Color(0xFFD9D1FF),
    tertiary = Color(0xFF5AA9F5),
    onTertiary = Color(0xFF06294A),
    tertiaryContainer = Color(0xFF12344F),
    onTertiaryContainer = Color(0xFFBFE0FF),
    background = Color(0xFF0F0E13),
    onBackground = Color(0xFFF2F1F5),
    surface = Color(0xFF0F0E13),
    onSurface = Color(0xFFF2F1F5),
    surfaceVariant = Color(0xFF2B2A32),
    onSurfaceVariant = Color(0xFFA9A6B3),
    surfaceContainerLowest = Color(0xFF17161C),
    surfaceContainerLow = Color(0xFF1B1A21),
    surfaceContainer = Color(0xFF211F27),
    surfaceContainerHigh = Color(0xFF29272F),
    surfaceContainerHighest = Color(0xFF33313A),
    outline = Color(0xFF7D7A87),
    outlineVariant = Color(0xFF2B2A32),
    error = Color(0xFFFF7A63),
    onError = Color(0xFF4A0F05),
    errorContainer = Color(0xFF4A1F18),
    onErrorContainer = Color(0xFFFFC9BE),
    inverseSurface = Color(0xFFF2F1F5),
    inverseOnSurface = Color(0xFF2C2B32),
    inversePrimary = Color(0xFF6246EA)
)

/** Fundo suave, texto sobre ele e cor forte (ícones e anéis) de cada estado. */
private data class Tom(val fundo: Color, val tinta: Color, val forte: Color)

private val FOLGA_CLARO = Tom(Color(0xFFD3F5E3), Color(0xFF0A5238), Color(0xFF0B8F5A))
private val FOLGA_ESCURO = Tom(Color(0xFF123D2C), Color(0xFFA9F0CB), Color(0xFF3DD598))
private val TRABALHO_CLARO = Tom(Color(0xFFFFE0DA), Color(0xFF8C2412), Color(0xFFD9432C))
private val TRABALHO_ESCURO = Tom(Color(0xFF4A1F18), Color(0xFFFFC9BE), Color(0xFFFF7A63))
private val FERIAS_CLARO = Tom(Color(0xFFD9ECFF), Color(0xFF0B4A80), Color(0xFF1F78D6))
private val FERIAS_ESCURO = Tom(Color(0xFF12344F), Color(0xFFBFE0FF), Color(0xFF5AA9F5))
private val ATENCAO_CLARO = Tom(Color(0xFFFFEDBF), Color(0xFF634500), Color(0xFFA86F00))
private val ATENCAO_ESCURO = Tom(Color(0xFF453400), Color(0xFFFFE29A), Color(0xFFF5B83D))
private val SONO_CLARO = Tom(Color(0xFFE4E2FB), Color(0xFF2F2B7A), Color(0xFF4A44B5))
private val SONO_ESCURO = Tom(Color(0xFF26245A), Color(0xFFD5D3FA), Color(0xFF8F8AF0))

@Composable
private fun tom(claro: Tom, escuro: Tom): Tom = if (isSystemInDarkTheme()) escuro else claro

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** Algarismos tabulares: números de datas e temperaturas alinham verticalmente. */
val TextStyle.numerico get() = copy(fontFeatureSettings = "tnum")

@Composable
fun FolguiTema(conteudo: @Composable () -> Unit) {
    val base = MaterialTheme.typography
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) CoresEscuras else CoresClaras,
        shapes = Formas,
        typography = base.copy(
            displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
            headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
        ),
        content = conteudo
    )
}

// Cores de cada situação do dia: folga (verde), férias (azul) e trabalho (coral).
// O coral é o mesmo do "ninguém livre": indisponível tem uma cor só no app inteiro.
@Composable
fun corFundo(status: StatusDia): Color = when (status) {
    StatusDia.FOLGA -> tom(FOLGA_CLARO, FOLGA_ESCURO).fundo
    StatusDia.FERIAS -> tom(FERIAS_CLARO, FERIAS_ESCURO).fundo
    StatusDia.TRABALHO -> tom(TRABALHO_CLARO, TRABALHO_ESCURO).fundo
}

@Composable
fun corConteudo(status: StatusDia): Color = when (status) {
    StatusDia.FOLGA -> tom(FOLGA_CLARO, FOLGA_ESCURO).tinta
    StatusDia.FERIAS -> tom(FERIAS_CLARO, FERIAS_ESCURO).tinta
    StatusDia.TRABALHO -> tom(TRABALHO_CLARO, TRABALHO_ESCURO).tinta
}

@Composable
fun corIndicador(status: StatusDia): Color = when (status) {
    StatusDia.FOLGA -> tom(FOLGA_CLARO, FOLGA_ESCURO).forte
    StatusDia.FERIAS -> tom(FERIAS_CLARO, FERIAS_ESCURO).forte
    StatusDia.TRABALHO -> tom(TRABALHO_CLARO, TRABALHO_ESCURO).forte
}

/** Trecho de sono nas barras de horário e na visão da semana. */
@Composable
fun corSono(): Color = tom(SONO_CLARO, SONO_ESCURO).forte

@Composable
fun corSonoFundo(): Color = tom(SONO_CLARO, SONO_ESCURO).fundo

@Composable
fun corSonoConteudo(): Color = tom(SONO_CLARO, SONO_ESCURO).tinta

// Âmbar: dia em que só parte do grupo está livre (e o sol da previsão do tempo)
@Composable
fun corAmbarFundo(): Color = tom(ATENCAO_CLARO, ATENCAO_ESCURO).fundo

@Composable
fun corAmbarConteudo(): Color = tom(ATENCAO_CLARO, ATENCAO_ESCURO).tinta

@Composable
fun corAmbar(): Color = tom(ATENCAO_CLARO, ATENCAO_ESCURO).forte

@Composable
fun corFundo(situacao: SituacaoGrupo): Color = when (situacao) {
    SituacaoGrupo.TODOS_LIVRES -> corFundo(StatusDia.FOLGA)
    SituacaoGrupo.PARCIAL -> corAmbarFundo()
    SituacaoGrupo.NINGUEM_LIVRE -> corFundo(StatusDia.TRABALHO)
}

@Composable
fun corConteudo(situacao: SituacaoGrupo): Color = when (situacao) {
    SituacaoGrupo.TODOS_LIVRES -> corConteudo(StatusDia.FOLGA)
    SituacaoGrupo.PARCIAL -> corAmbarConteudo()
    SituacaoGrupo.NINGUEM_LIVRE -> corConteudo(StatusDia.TRABALHO)
}

@Composable
fun corIndicador(situacao: SituacaoGrupo): Color = when (situacao) {
    SituacaoGrupo.TODOS_LIVRES -> corIndicador(StatusDia.FOLGA)
    SituacaoGrupo.PARCIAL -> corAmbar()
    SituacaoGrupo.NINGUEM_LIVRE -> corIndicador(StatusDia.TRABALHO)
}

// Dia na escolha de um evento feito sozinho (ver DisponibilidadeSolo)
@Composable
fun corFundo(d: DisponibilidadeSolo): Color = when (d) {
    DisponibilidadeSolo.FOLGA -> corFundo(StatusDia.FOLGA)
    DisponibilidadeSolo.FERIAS -> corFundo(StatusDia.FERIAS)
    DisponibilidadeSolo.TRABALHA_LIVRE -> corAmbarFundo()
    DisponibilidadeSolo.OCUPADO -> corFundo(StatusDia.TRABALHO)
}

@Composable
fun corConteudo(d: DisponibilidadeSolo): Color = when (d) {
    DisponibilidadeSolo.FOLGA -> corConteudo(StatusDia.FOLGA)
    DisponibilidadeSolo.FERIAS -> corConteudo(StatusDia.FERIAS)
    DisponibilidadeSolo.TRABALHA_LIVRE -> corAmbarConteudo()
    DisponibilidadeSolo.OCUPADO -> corConteudo(StatusDia.TRABALHO)
}

@Composable
fun corIndicador(d: DisponibilidadeSolo): Color = when (d) {
    DisponibilidadeSolo.FOLGA -> corIndicador(StatusDia.FOLGA)
    DisponibilidadeSolo.FERIAS -> corIndicador(StatusDia.FERIAS)
    DisponibilidadeSolo.TRABALHA_LIVRE -> corAmbar()
    DisponibilidadeSolo.OCUPADO -> corIndicador(StatusDia.TRABALHO)
}

// Cor de cada pessoa (avatar e linha do tempo); "você" usa a cor principal do app
private val CORES_PESSOAS_CLARAS = listOf(
    Color(0xFFFFD8E4) to Color(0xFF3E001D),
    Color(0xFFD5E3FF) to Color(0xFF001B3C),
    Color(0xFFFFDDB8) to Color(0xFF2C1600),
    Color(0xFFE9DDFF) to Color(0xFF22005D),
    Color(0xFFD3E8A6) to Color(0xFF141F00),
    Color(0xFFFFDAD4) to Color(0xFF3B0907)
)
private val CORES_PESSOAS_ESCURAS = listOf(
    Color(0xFF7B2949) to Color(0xFFFFD8E4),
    Color(0xFF244777) to Color(0xFFD5E3FF),
    Color(0xFF6A4000) to Color(0xFFFFDDB8),
    Color(0xFF4F378A) to Color(0xFFE9DDFF),
    Color(0xFF3C4D12) to Color(0xFFD3E8A6),
    Color(0xFF73342A) to Color(0xFFFFDAD4)
)

/** (fundo, conteúdo) do avatar da pessoa. */
@Composable
fun coresPessoa(pessoa: Pessoa): Pair<Color, Color> {
    if (pessoa.ehVoce) return MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    val paleta = if (isSystemInDarkTheme()) CORES_PESSOAS_ESCURAS else CORES_PESSOAS_CLARAS
    return paleta[Math.floorMod(pessoa.id, paleta.size)]
}

fun iconeSituacao(situacao: SituacaoGrupo): ImageVector = when (situacao) {
    SituacaoGrupo.TODOS_LIVRES -> Icones.DiaLivre
    SituacaoGrupo.PARCIAL -> Icones.Aviso
    SituacaoGrupo.NINGUEM_LIVRE -> Icones.Relogio
}

// ============================================================================
// Ícones desenhados a partir de caminhos SVG (24x24)
// ============================================================================
object Icones {
    val Inicio = icone("M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z")
    val Calendario = icone(
        "M20,3h-1V1h-2v2H7V1H5v2H4c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V5" +
            "c0,-1.1 -0.9,-2 -2,-2zM20,21H4V8h16v13z"
    )
    val Pessoas = icone(
        "M16,11c1.66,0 2.99,-1.34 2.99,-3S17.66,5 16,5c-1.66,0 -3,1.34 -3,3s1.34,3 3,3z" +
            "M8,11c1.66,0 2.99,-1.34 2.99,-3S9.66,5 8,5C6.34,5 5,6.34 5,8s1.34,3 3,3z" +
            "M8,13c-2.33,0 -7,1.17 -7,3.5V19h14v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z" +
            "M16,13c-0.29,0 -0.62,0.02 -0.97,0.05 1.16,0.84 1.97,1.97 1.97,3.45V19h6v-2.5" +
            "c0,-2.33 -4.67,-3.5 -7,-3.5z"
    )
    val Evento = icone(
        "M17,12h-5v5h5v-5zM16,1v2H8V1H6v2H5c-1.11,0 -1.99,0.9 -1.99,2L3,19c0,1.1 0.89,2 2,2h14" +
            "c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2h-1V1h-2zM19,19H5V8h14v11z"
    )
    val DiaLivre = icone(
        "M19,4h-1V2h-2v2H8V2H6v2H5C3.89,4 3,4.9 3,6v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6" +
            "C21,4.9 20.1,4 19,4zM19,20H5V10h14V20zM10.5,17.5l-3.5,-3.5l1.41,-1.41L10.5,14.67" +
            "l5.59,-5.59L17.5,10.5L10.5,17.5z"
    )
    val Adicionar = icone("M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    val Voltar = icone("M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    val Fechar = icone(
        "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z"
    )
    val Confirmar = icone("M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z")
    val Anterior = icone("M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z")
    val Proximo = icone("M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z")
    val Editar = icone(
        "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41" +
            "l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z"
    )
    val Excluir = icone("M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z")
    val Local = icone(
        "M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13c0,-3.87 -3.13,-7 -7,-7z" +
            "M12,11.5c-1.38,0 -2.5,-1.12 -2.5,-2.5s1.12,-2.5 2.5,-2.5 2.5,1.12 2.5,2.5 -1.12,2.5 -2.5,2.5z"
    )
    val Relogio = icone(
        "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2z" +
            "M12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z" +
            "M12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z"
    )
    val Sino = icone(
        "M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4" +
            "c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z"
    )
    val Sol = icone(
        "M6.76,4.84l-1.8,-1.79 -1.41,1.41 1.79,1.79 1.42,-1.41zM4,10.5H1v2h3v-2zM13,0.55h-2V3.5h2V0.55z" +
            "M20.45,4.46l-1.41,-1.41 -1.79,1.79 1.41,1.41 1.79,-1.79zM17.24,18.16l1.79,1.8 1.41,-1.41 -1.8,-1.79 -1.4,1.4z" +
            "M20,10.5v2h3v-2h-3zM12,5.5c-3.31,0 -6,2.69 -6,6s2.69,6 6,6 6,-2.69 6,-6 -2.69,-6 -6,-6z" +
            "M11,22.45h2V19.5h-2v2.95zM3.55,18.54l1.41,1.41 1.79,-1.8 -1.41,-1.41 -1.79,1.8z"
    )
    val Nuvem = icone(
        "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14" +
            "c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96z"
    )
    val Gota = icone("M12,2c-5.33,4.55 -8,8.48 -8,11.8c0,4.98 3.8,8.2 8,8.2s8,-3.22 8,-8.2C20,10.48 17.33,6.55 12,2z")
    val Email = icone(
        "M20,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2z" +
            "M20,8l-8,5 -8,-5V6l8,5 8,-5v2z"
    )
    val Sincronizar = icone(
        "M12,4V1L8,5l4,4V6c3.31,0 6,2.69 6,6 0,1.01 -0.25,1.97 -0.7,2.8l1.46,1.46C19.54,15.03 20,13.57 20,12" +
            "c0,-4.42 -3.58,-8 -8,-8zM12,18c-3.31,0 -6,-2.69 -6,-6 0,-1.01 0.25,-1.97 0.7,-2.8L5.24,7.74" +
            "C4.46,8.97 4,10.43 4,12c0,4.42 3.58,8 8,8v3l4,-4 -4,-4v3z"
    )
    val Aviso = icone("M1,21h22L12,2 1,21zM13,18h-2v-2h2v2zM13,14h-2v-4h2v4z")
    val Brilho = icone(
        "M19,9l1.25,-2.75L23,5l-2.75,-1.25L19,1l-1.25,2.75L15,5l2.75,1.25L19,9z" +
            "M11.5,9.5L9,4L6.5,9.5L1,12l5.5,2.5L9,20l2.5,-5.5L17,12L11.5,9.5z" +
            "M19,15l-1.25,2.75L15,19l2.75,1.25L19,23l1.25,-2.75L23,19l-2.75,-1.25L19,15z"
    )
    val Enviar = icone("M2.01,21L23,12 2.01,3 2,10l15,2 -15,2z")
    val Lua = icone(
        "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36" +
            "c-0.98,1.37 -2.58,2.26 -4.4,2.26 -2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4" +
            "C12.92,3.04 12.46,3 12,3z"
    )
    val Trabalho = icone(
        "M20,6h-4V4c0,-1.11 -0.89,-2 -2,-2h-4c-1.11,0 -2,0.89 -2,2v2H4c-1.11,0 -1.99,0.89 -1.99,2L2,19" +
            "c0,1.11 0.89,2 2,2h16c1.11,0 2,-0.89 2,-2V8c0,-1.11 -0.89,-2 -2,-2zM14,6h-4V4h4v2z"
    )

    val Escola = icone("M5,13.18v4L12,21l7,-3.82v-4L12,17l-7,-3.82zM12,3L1,9l11,6 9,-4.91V17h2V9L12,3z")
    val Colunas = icone("M3,5h5v14H3zM9.5,5h5v14h-5zM16,5h5v14h-5z")
    val Lista = icone("M3,6h18v2H3zM3,11h18v2H3zM3,16h18v2H3z")
    val Grade = icone("M3,3h8v8H3zM13,3h8v8h-8zM3,13h8v8H3zM13,13h8v8h-8z")
    val Microfone = icone(
        "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3z" +
            "M17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28" +
            "c3.28,-0.48 6,-3.3 6,-6.72h-1.7z"
    )
    val Estrela = icone(
        "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z"
    )

    private fun icone(caminho: String): ImageVector =
        ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(pathData = addPathNodes(caminho), fill = SolidColor(Color.Black)).build()
}

fun iconeTempo(codigo: Int): ImageVector = when (codigo) {
    0, 1 -> Icones.Sol
    2, 3, 45, 48 -> Icones.Nuvem
    else -> Icones.Gota
}
