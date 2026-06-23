pipeline {

    agent any

    // ─────────────────────────────────────────────────────────────────────────
    // ZAMANLAMA — istediğin sıklığı buradan değiştir
    //   Her 5 saatte bir  →  'H */5 * * *'
    //   Her gün 08:00'de  →  '0 8 * * *'
    //   Her saat başı     →  'H * * * *'
    //   Sadece manuel     →  schedule satırını sil veya yorum satırı yap
    // ─────────────────────────────────────────────────────────────────────────
    triggers {
        cron('H */5 * * *')
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AYARLAR — Jenkins → Manage Jenkins → Configure System üzerinden
    // ya da direkt buradan düzenle
    // ─────────────────────────────────────────────────────────────────────────
    environment {
        // Mail bildirimi — kapatmak için false yap
        MAIL_ENABLED     = 'true'
        MAIL_RECIPIENTS  = 'baris.durak@inomera.com, takim@inomera.com'

        // Slack bildirimi — kapatmak için false yap
        SLACK_ENABLED    = 'true'
        SLACK_CHANNEL    = '#muud-arama-test'          // kanalı değiştir

        // Hangi test sınıfları koşsun (virgülle ayır)
        TEST_CLASSES     = 'Bulgu_UAT_OrtakExcel,SemanticSearchTest'

        // Maven
        MAVEN_OPTS       = '-Xmx512m'
    }

    stages {

        // ── 1. Kaynak kodu al ─────────────────────────────────────────────
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        // ── 2. Bağımlılıkları indir ───────────────────────────────────────
        stage('Build') {
            steps {
                sh 'mvn clean compile test-compile -q'
            }
        }

        // ── 3. Testleri koştur ────────────────────────────────────────────
        stage('Run Tests') {
            steps {
                sh """
                    mvn test \
                        -Dtest=${TEST_CLASSES} \
                        -DbaseUrl=${BASE_URL} \
                        -Dtoken=${SEARCH_TOKEN} \
                        -q
                """
            }
            post {
                always {
                    // Excel raporları artifact olarak sakla
                    archiveArtifacts artifacts: '*.xlsx', allowEmptyArchive: true
                    // JUnit XML varsa göster (opsiyonel)
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

    }

    // ── Pipeline bitti: bildirim gönder ──────────────────────────────────────
    post {

        always {
            script {
                def duration  = currentBuild.durationString.replace(' and counting', '')
                def buildUrl  = env.BUILD_URL
                def jobName   = env.JOB_NAME
                def buildNum  = env.BUILD_NUMBER
                def status    = currentBuild.currentResult   // SUCCESS / FAILURE / UNSTABLE
                def emoji     = status == 'SUCCESS' ? '✅' : '❌'
                def statusTR  = status == 'SUCCESS' ? 'BAŞARILI' : 'BAŞARISIZ'

                // ── SLACK ─────────────────────────────────────────────────
                if (env.SLACK_ENABLED == 'true') {
                    def color = status == 'SUCCESS' ? 'good' : 'danger'
                    slackSend(
                        channel : env.SLACK_CHANNEL,
                        color   : color,
                        message : """${emoji} *MUUD Arama Test Raporu — ${statusTR}*
Job     : ${jobName} #${buildNum}
Süre    : ${duration}
Rapor   : ${buildUrl}artifact/
Detay   : ${buildUrl}"""
                    )
                }

                // ── MAİL ──────────────────────────────────────────────────
                if (env.MAIL_ENABLED == 'true') {
                    def subject = "${emoji} MUUD Arama Test Raporu — ${statusTR} | Build #${buildNum}"
                    def body = """
<html><body style="font-family:Arial,sans-serif;font-size:14px;">

<h2>${emoji} MUUD Arama Test Raporu</h2>

<table border="1" cellpadding="8" cellspacing="0" style="border-collapse:collapse;">
  <tr><td><b>Durum</b></td>   <td><b>${statusTR}</b></td></tr>
  <tr><td>Job</td>            <td>${jobName} #${buildNum}</td></tr>
  <tr><td>Süre</td>           <td>${duration}</td></tr>
  <tr><td>Tarih</td>          <td>${new Date()}</td></tr>
  <tr><td>Excel Rapor</td>    <td><a href="${buildUrl}artifact/">Buradan indir</a></td></tr>
  <tr><td>Build Detayı</td>   <td><a href="${buildUrl}">Jenkins'te görüntüle</a></td></tr>
</table>

<p style="color:gray;font-size:12px;">
  Bu mail otomatik olarak gönderilmiştir.<br>
  Koşulan testler: ${env.TEST_CLASSES}
</p>

</body></html>
"""
                    emailext(
                        subject     : subject,
                        body        : body,
                        mimeType    : 'text/html',
                        to          : env.MAIL_RECIPIENTS,
                        attachmentsPattern : '*.xlsx'
                    )
                }
            }
        }

    }
}
