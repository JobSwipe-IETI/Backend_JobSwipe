
# JobSwipe AI - Backend

## Descripción del Proyecto

JobSwipe AI es una plataforma inteligente de reclutamiento que utiliza inteligencia artificial para realizar matching semántico entre candidatos y empresas.

El backend del proyecto gestiona la lógica principal del sistema, incluyendo:

- Registro y autenticación de usuarios (candidatos y empresas)
- Publicación y gestión de vacantes
- Cálculo del score de compatibilidad entre perfiles y vacantes
- Generación automática de cartas de presentación
- Evaluación básica de pruebas técnicas
- Gestión de matches bidireccionales

Este repositorio corresponde a la configuración inicial del MVP bajo la metodología Trunk-Based Development.

---

## Versión del Lenguaje y Framework

- Java 17
- Spring Boot 3.x
- Maven 3.x

---

## Dependencias Iniciales

Las dependencias principales del proyecto son:

- spring-boot-starter-web
- spring-boot-starter-data-jpa
- postgresql
- spring-boot-starter-security
- lombok
- spring-boot-devtools

---

## Estructura Base del Proyecto

```
src/main/java/IETI/JobSwipe
 ├── controller
 ├── service
 ├── repository
 ├── model
 ├── config
```

---

## Instalación y Ejecución

### 1. Clonar el repositorio

```bash
git clone https://github.com/JobSwipe-IETI/Backend_JobSwipe.git
cd feature/initial-structure
```

### 2. Ejecutar el proyecto

```bash
mvn spring-boot:run
```

### Cache de recomendaciones (produccion)

Para evitar recalcular el matching IA en cada carga de recomendaciones, el backend usa cache persistente por usuario+vacante con TTL.
El score se recalcula solo si:

- la vacante es nueva
- cambió el perfil del candidato
- cambió la vacante
- expiró el TTL de cache

1. Crear tabla en PostgreSQL con el script:

```bash
scripts/create_recommendation_cache.sql
```

2. Configurar TTL (opcional):

```bash
RECOMMENDATIONS_CACHE_TTL_SECONDS=1800
```

Valor por defecto: `1800` segundos (30 minutos).


El servidor iniciará en:

http://localhost:8080

---

## Endpoint de Prueba

Para verificar que el backend funciona correctamente:

GET /api/health

Ejemplo:

http://localhost:8080/api/health

Respuesta esperada:

```json
{
  "status": "UP"
}
```

---

## Autenticación con Google

El backend valida un Google ID token enviado por el cliente en el header `Authorization` con formato `Bearer <token>`.

### Configuración requerida

Definir la variable de entorno `GOOGLE_OAUTH_CLIENT_ID` con el Client ID de Google configurado para la aplicación.

### Endpoint para obtener identidad autenticada

`GET /auth/me`

Respuesta esperada:

```json
{
  "email": "usuario@correo.com",
  "name": "Nombre Apellido"
}
```

### Endpoints públicos

- `GET /api/health`
- Swagger UI y OpenAPI

### Endpoints protegidos

Todos los demás endpoints requieren un Google ID token válido.

---

## Planeación del Proyecto

La planeación detallada del desarrollo del backend, incluyendo backlog inicial, historias de usuario y planificación de sprint, está disponible en el siguiente enlace:

[Planeación del Backend - JobSwipe AI](https://dev.azure.com/alisonvalderrama-m/JobSwipe)

---

## Metodología de Desarrollo

El proyecto sigue la metodología Trunk-Based Development, utilizando:

- Rama principal: main
- Ramas cortas para funcionalidades
- Pull requests pequeños y frecuentes
- Integración continua progresiva

---

## Estado Actual del MVP

- Configuración inicial del proyecto
- Estructura base organizada
- Endpoint de prueba implementado
- Próxima fase: implementación de autenticación y gestión de usuarios
