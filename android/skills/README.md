# skills/ — Contexto para agentes de IA

Esta carpeta NO contiene código de la app: es **documentación orientada a agentes de IA**
(Claude Code, Copilot, etc.) que vayan a trabajar en este repositorio. Su objetivo es darle a
un agente el contexto que **no** se deduce solo leyendo el código: convenciones, decisiones de
diseño, trampas conocidas y comandos para construir/probar.

## Contenido

| Archivo | Para qué |
|---|---|
| [`project-onboarding.md`](project-onboarding.md) | Punto de entrada: qué es el proyecto, cómo está organizado, cómo compilar/probar, y el mapa de "dónde tocar para X". |
| [`conventions.md`](conventions.md) | Convenciones de código y **trampas conocidas** (no repetir errores ya pagados). |
| [`debug-hooks.md`](debug-hooks.md) | Hooks de debug por broadcast y tags de logcat para manejar/observar el servicio en tests e2e. |

## Reglas rápidas para el agente

1. **Idioma:** el proyecto y el usuario trabajan en **español**. Comentarios, commits y docs
   en español.
2. **Buenas prácticas primero:** el dueño insistió explícitamente en respetarlas (arquitectura
   limpia, testeable, sin atajos). Ante la duda, priorizá claridad y separación de capas.
3. **Antes de cambiar comportamiento, leé** [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md)
   y [`../TESTING.md`](../TESTING.md).
4. **No subas nada sensible** — ver `.gitignore` endurecido (keystores, `.env`, etc.).
5. **Windows:** no corras Gradle por terminal y Android Studio sincronizando a la vez.
