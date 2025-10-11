# Docker Container Monitor

A Jenkins pipeline for automated Docker container monitoring with Discord notifications.

## Overview

This pipeline periodically checks the health status of specified Docker containers and sends alerts to Discord when issues are detected. It differentiates between high-priority and low-priority containers and implements smart alerting to avoid notification spam.

## Features

- **Scheduled Monitoring**: Runs every 2 minutes via cron trigger
- **Container Health Checks**: Monitors running status and health checks
- **Priority-based Alerting**: 
  - High-priority containers trigger critical alerts
  - Low-priority containers send warnings
- **Smart Notification System**: Prevents alert spam with configurable intervals
- **Discord Integration**: Sends formatted embeds with color-coded alerts
- **Artifact Reporting**: Generates and archives monitoring reports

## Configuration

### Environment Variables

- `CONTAINERS_TO_MONITOR`: Comma-separated list of container names to monitor
- `CONTAINERS_NOT_PRIMARY`: Comma-separated list of low-priority containers
- `DISCORD_WEBHOOK`: Jenkins credential containing Discord webhook URL

### Alert Intervals

- **High-priority containers**: 30min, 1h, 6h, 6h, 12h intervals
- **Low-priority containers**: 6-hour intervals
- **Recovery notifications**: Sent when containers return to normal state

## Pipeline Structure

1. **Monitoring Stage**: Checks container status and health
2. **Post-processing**: Sends appropriate Discord notifications
3. **Artifact Archiving**: Saves monitoring reports

## Requirements

- Jenkins with Docker support
- Docker daemon access
- Discord webhook URL configured as Jenkins credential
