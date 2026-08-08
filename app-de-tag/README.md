# Editor de Tags

Editor de etiquetas (tags) para produtos artesanais, com impressão em frente e verso.
Roda inteiro no navegador — não há servidor, build ou dependência para instalar.

## Como usar

Abra o `index.html` no navegador, ou acesse a versão publicada pelo GitHub Pages.

1. Escolha um **modelo pronto** (mel, luxo, natural, suculentas, doces, geleias, genérico).
2. Ajuste os textos, cores, fontes, formato e enfeites no painel da esquerda.
3. Configure a **página de impressão**: tamanho fixo em centímetros ou preenchendo a folha por colunas × linhas.
4. Clique em **Imprimir / PDF** e escolha "Salvar como PDF" ou mande para a impressora.

A página 1 sai com as frentes e a página 2 com os versos. A opção **espelhar verso**
mantém o alinhamento em impressoras que viram a folha pelo lado comprido.

## Recursos

- Formatos de tag: etiqueta clássica, retângulo arredondado, hexágono e círculo
- Bordas decorativas, divisórias e ícones em SVG, com tamanho e espessura ajustáveis
- Padrões de fundo com controle de opacidade e área de aplicação
- Upload da sua própria imagem ou logo (PNG, JPEG ou SVG)
- Reordenação dos itens da frente e do verso por arrastar-e-soltar
- Paletas de cores rápidas e mais de 30 fontes
- Furo para cordão com tamanho configurável

## Detalhes técnicos

Arquivo único, HTML + CSS + JavaScript sem frameworks. O único recurso externo são as
fontes do Google Fonts. As configurações ficam salvas no `localStorage` do navegador,
e a imagem enviada nunca sai da máquina.
