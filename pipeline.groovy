pipeline {
    agent any
    triggers {
        cron('*/2 * * * *')
    }
    
    environment {
        CONTAINERS_TO_MONITOR = 'container1,container2'
        CONTAINERS_NOT_PRIMARY = 'container2'
        DISCORD_WEBHOOK = credentials('DISCORD_PRUEBA')
    }
    
    stages {
        stage('Monitoreo de Contenedores') {
            steps {
                script {
                    def containers = env.CONTAINERS_TO_MONITOR.split(',')
                    def failedContainers = []
                    def unhealthyContainers = []
                    def unexpectedStoppedContainers = []
                    def lowPriorityStopped = []
                    def highPriorityStopped = []
                    
                    def nonPrimary = env.CONTAINERS_NOT_PRIMARY.split(',')
                    
                    containers.each { containerName ->
                        def runningContainer = sh(
                            script: "docker ps -q --filter \"name=${containerName}\"",
                            returnStdout: true
                        ).trim()
                        
                        if (!runningContainer) {
                            def stoppedContainer = sh(
                                script: "docker ps -a -q --filter \"name=${containerName}\" --filter \"status=exited\"",
                                returnStdout: true
                            ).trim()
                            
                            if (stoppedContainer) {
                                echo "❌ Contenedor ${containerName} se detuvo inesperadamente"
                                unexpectedStoppedContainers.add(containerName)
                                failedContainers.add(containerName)
                                
                                if (nonPrimary.contains(containerName)) {
                                    lowPriorityStopped.add(containerName)
                                } else {
                                    highPriorityStopped.add(containerName)
                                }
                            } else {
                                failedContainers.add(containerName)
                                echo "❌ Contenedor ${containerName} no existe"
                            }
                        } else {
                            def healthStatus = sh(
                                script: "docker inspect --format='{{.State.Health.Status}}' ${containerName} 2>/dev/null || echo 'no-healthcheck'",
                                returnStdout: true
                            ).trim()
                            
                            if (healthStatus == 'unhealthy') {
                                unhealthyContainers.add(containerName)
                                echo "🟡 Contenedor ${containerName} está unhealthy"
                            } else {
                                echo "✅ Contenedor ${containerName} está saludable"
                            }
                        }
                    }
                    
                    // Guardar resultados
                    env.FAILED_CONTAINERS = failedContainers.join(', ')
                    env.UNHEALTHY_CONTAINERS = unhealthyContainers.join(', ')
                    env.UNEXPECTED_STOPPED = unexpectedStoppedContainers.join(', ')
                    env.UNEXPECTED_NO_PRIORITY_STOPPED = lowPriorityStopped.join(', ')
                    env.UNEXPECTED_PRIORITY_STOPPED = highPriorityStopped.join(', ')
                    env.TOTAL_PROBLEMS = failedContainers.size() + unhealthyContainers.size()
                    
                    echo "=== RESUMEN ==="
                    echo "Detenidos inesperadamente: ${unexpectedStoppedContainers}"
                    echo "De baja prioridad: ${lowPriorityStopped}"
                    echo "De alta prioridad: ${highPriorityStopped}"
                    echo "Unhealthy: ${unhealthyContainers}"
                    echo "Total problemas: ${env.TOTAL_PROBLEMS}"
                }
            }
        }
    }
    
    post {
        always {
            script {
                def report = """Reporte de Monitoreo Docker - ${currentBuild.currentResult}

Contenedores detenidos inesperadamente: ${env.UNEXPECTED_STOPPED ?: 'Ninguno'}
Contenedores unhealthy: ${env.UNHEALTHY_CONTAINERS ?: 'Ninguno'}
Total de problemas: ${env.TOTAL_PROBLEMS}

Tiempo: ${new Date()}
"""
                writeFile file: 'monitoring_report.txt', text: report
                archiveArtifacts artifacts: 'monitoring_report.txt', allowEmptyArchive: true
            }
        }
        
        success {
            script {
                // Si hay caídas de ALTA prioridad → FALLA CRÍTICA
                if (env.UNEXPECTED_PRIORITY_STOPPED) {
                    echo "Enviando notificación de fallo crítico"
                    sendDiscordNotification(
                        "❌ **FALLA CRÍTICA**",
                        "Ha ocurrido una falla crítica en contenedores de alta prioridad.",
                        "FF0000",
                        [
                            [name: "Contenedores críticos caídos:", value: "${env.UNEXPECTED_PRIORITY_STOPPED}", inline: false],
                            [name: "Contenedores unhealthy:", value: "${env.UNHEALTHY_CONTAINERS ?: 'Ninguno'}", inline: false]
                        ]
                    )
                }

                // Si hay BAJA prioridad → AVISO
                if (env.UNEXPECTED_NO_PRIORITY_STOPPED) {
                    def alertFile = "${env.WORKSPACE}/last_low_priority_alert.txt"
                    def currentLowPriority = env.UNEXPECTED_NO_PRIORITY_STOPPED.split(',').collect { it.trim() }.sort()
                    def now = System.currentTimeMillis()
                    def resend = false
                    def intervals = [10*60*1000, 60*60*1000, 6*60*60*1000] // 30 min, 1h, 6h
                
                    if (fileExists(alertFile)) {
                        def content = readFile(file: alertFile).trim()
                        def (prevContainersStr, lastAlertStr) = content.tokenize('|')
                        def previousContainers = prevContainersStr ? prevContainersStr.split(',').collect { it.trim() }.sort() : []
                        def lastAlert = lastAlertStr?.isNumber() ? lastAlertStr.toLong() : 0L
                
                        def sameContainers = previousContainers == currentLowPriority
                        def elapsed = now - lastAlert
                
                        if (sameContainers) {
                            for (interval in intervals) {
                                if (elapsed >= interval && elapsed < interval + (2*60*1000)) { // tolerancia 2 min
                                    resend = true
                                    break
                                }
                            }
                        } else {
                            // Cambió la lista → nuevo aviso
                            resend = true
                        }
                    } else {
                        // Primer aviso
                        resend = true
                    }
                
                    if (resend) {
                        echo "🔁 Enviando aviso (nuevo o repetido por persistencia del problema)"
                        sendDiscordNotification(
                            "⚠️ **AVISO**",
                            "Uno o más contenedores de baja prioridad se encuentran detenidos.",
                            "FFA500",
                            [
                                [name: "Contenedores de baja prioridad caídos:", value: "${env.UNEXPECTED_NO_PRIORITY_STOPPED}", inline: false]
                            ]
                        )
                        writeFile file: alertFile, text: "${currentLowPriority.join(',')}|${now}"
                    } else {
                        echo "⏱️ No se cumple intervalo de reenvío, no se enviará aviso."
                    }
                } else {
                    // Si ya no hay contenedores caídos, eliminamos el archivo de estado
                    def alertFile = "${env.WORKSPACE}/last_low_priority_alert.txt"
                    if (fileExists(alertFile)) {
                        echo "🟢 Todos los contenedores de baja prioridad están activos. Limpiando estado..."
                        sh "rm -f ${alertFile}"
                    }
                }

                
                // Notificación de unhealthy
                if (env.UNHEALTHY_CONTAINERS) {
                    echo "Enviando notificación de no saludable"
                    sendDiscordNotification(
                        "⚠️ **PROBLEMAS DE SALUD**",
                        "**Contenedores unhealthy:** ${env.UNHEALTHY_CONTAINERS}",
                        "FFA500",
                        [
                            [name: "Contenedores unhealthy:", value: "${env.UNHEALTHY_CONTAINERS}", inline: false]
                        ]
                    )
                }
            }
        }
    }
}

// Función de notificación
def sendDiscordNotification(title, description, color, extraFields) {
    echo "Enviando notificación a Discord: ${title}"
    try {
        def fields = []
        if (extraFields) {
            extraFields.each { field ->
                fields.add("""{
                    "name": "${field.name.replace('"', '\\"')}",
                    "value": "${(field.value ?: 'Ninguno').replace('"', '\\"')}",
                    "inline": ${field.inline}
                }""")
            }
        }
        fields.add("""{
            "name": "Contenedores monitoreados",
            "value": "${env.CONTAINERS_TO_MONITOR.split(',').size()}",
            "inline": false
        }""")
        fields.add("""{
            "name": "Estado del pipeline",
            "value": "${currentBuild.currentResult}",
            "inline": false
        }""")
        
        def jsonPayload = """{
    "embeds": [
        {
            "title": "${title.replace('"', '\\"')}",
            "description": "${description.replace('"', '\\"')}",
            "color": ${Integer.parseInt(color, 16)},
            "timestamp": "${new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))}",
            "footer": { "text": "Jenkins Docker Monitor" },
            "fields": [ ${fields.join(",\n")} ]
        }
    ]
}"""
        
        writeFile file: 'discord_debug.json', text: jsonPayload
        sh "cat discord_debug.json"
        
        withCredentials([string(credentialsId: 'DISCORD_PRUEBA', variable: 'DISCORD_WEBHOOK')]) {
            sh ('''
              curl -s -X POST -H "Content-Type: application/json" \
                   -d @discord_debug.json \
                   $DISCORD_WEBHOOK
            ''')
        }
        
        sh "rm -f discord_debug.json"
        echo "✅ Notificación enviada a Discord"
    } catch (Exception e) {
        echo "❌ Error enviando notificación: ${e.getMessage()}"
    }
}