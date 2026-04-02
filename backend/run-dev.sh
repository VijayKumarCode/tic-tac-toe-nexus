#!/bin/bash
export DB_URL="jdbc:postgresql://ep-muddy-cherry-a1gle0fk-pooler.ap-southeast-1.aws.neon.tech/nexus_db?sslmode=require&channelBinding=require"
export DB_USERNAME="neondb_owner"
export DB_PASSWORD="npg_e2EySnv4wJhN"
export RESEND_API_KEY="re_5vy5ygoV_A4P8mppPzou6eoVsZrho4LJ3"
export MAIL_FROM="noreply@nexusgame.space"
export APP_BASE="http://localhost:8080"

mvn spring-boot:run -Dspring-boot.run.profiles=dev
