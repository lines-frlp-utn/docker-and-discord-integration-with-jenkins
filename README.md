# Docker Container Monitor

Un pipeline de Jenkins para la monitorización automatizada de contenedores Docker con notificaciones de Discord.

## Resumen

Este pipeline comprueba periódicamente el estado de salud de los contenedores Docker especificados y envía alertas a Discord cuando se detectan problemas. Diferencia entre contenedores de alta prioridad y de baja prioridad e implementa un sistema de alerta inteligente para evitar el spam de notificaciones.

## Características

- **Monitorización Programada**: Se ejecuta cada 2 minutos a través de un disparador cron
- **Comprobaciones de Salud de Contenedores**: Monitoriza el estado de ejecución y las comprobaciones de salud (`health checks`)
- **Alertas Basadas en Prioridad**: 
  - Los contenedores de alta prioridad activan alertas críticas
  - Los contenedores de baja prioridad envían advertencias
- **Sistema de Notificación Inteligente**: Previene el spam de alertas con intervalos configurables
- **Integración con Discord**: Envía mensajes formateados (embeds) con alertas codificadas por colores
- **Informes como Artefactos**: Genera y archiva informes de monitorización

## Configuración

### Variables de Entorno

- `CONTAINERS_TO_MONITOR`: Lista de nombres de contenedores a monitorizar, separados por comas
- `CONTAINERS_NOT_PRIMARY`: Lista de nombres de contenedores de baja prioridad, separados por comas
- `DISCORD_WEBHOOK`: Credencial de Jenkins que contiene la URL del webhook de Discord

### Intervalos de Alerta

- **Contenedores de alta prioridad**: Intervalos de 30min, 1h, 6h, 6h, 12h
- **Contenedores de baja prioridad**: Intervalos de 6 horas
- **Notificaciones de Recuperación**: Se envían cuando los contenedores vuelven a su estado normal

## Estructura del Pipeline

1. **Etapa de Monitorización**: Comprueba el estado y la salud de los contenedores
2. **Post-procesamiento**: Envía las notificaciones de Discord apropiadas
3. **Archivo de Artefactos**: Guarda los informes de monitorización

## Requisitos

- Jenkins con soporte para Docker
- Acceso al daemon de Docker
- URL del webhook de Discord configurada como credencial de Jenkins
