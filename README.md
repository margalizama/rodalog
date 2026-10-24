# RodaLog 🚗🏍️

Bitácora de **auto y moto** para Android, con **Google Sheets como base de datos**.
Sin anuncios, sin servidor: tus datos viven en el teléfono y se sincronizan con *tu* hoja de Google.

## Funciones

- **Varios vehículos** (autos y motos) con tanque, odómetro y consumo base.
- **Combustible**: cargas, costo, precio por litro/galón, consumo por tanque lleno y gráfica de tendencia.
- **Gastos y servicios**: servicio, reparación, llantas, seguro, multas, casetas, etc. con totales por categoría.
- **Recordatorios** por fecha y/o kilometraje, con repetición y notificación.
- **Viajes con GPS automáticos**: se arman al conectarse el Bluetooth vinculado al vehículo y empiezan a grabar al detectar movimiento.
- **Computadora de viaje**: velocidad, distancia, tiempo, velocidad media/máxima, consumo estimado, combustible usado, **tanque restante**, autonomía y **odómetro** que se va sumando.
- **Sin internet**: todo se guarda local y se sincroniza cuando hay conexión.
- **Compartir**: comparte la hoja de Google con tu familia; todos ven y editan los mismos vehículos.
- **Idioma, unidades y moneda** según el sistema (español / inglés; km o mi; L o gal).
- Tema claro/oscuro automático.

## Instalar

Descarga `RodaLog.apk` de la sección **Releases** (etiqueta `latest`) e instálalo
(permite "instalar apps de orígenes desconocidos" cuando Android lo pida).
Cada push a `main` compila y publica un APK nuevo con GitHub Actions.

## Configurar Google Sheets (una sola vez)

La app usa tu cuenta de Google para leer/escribir tu hoja. Android exige que la app esté registrada:

1. Entra a <https://console.cloud.google.com> y crea un proyecto (p. ej. “RodaLog”).
2. **APIs y servicios → Biblioteca →** activa **Google Sheets API**.
3. **Pantalla de consentimiento OAuth →** tipo *Externo*; en *Usuarios de prueba* agrega tu correo (y el de tu familia).
   Agrega el alcance `.../auth/spreadsheets`.
4. **Credenciales → Crear credenciales → ID de cliente de OAuth → Android**:
   - Nombre del paquete: `app.rodalog`
   - Huella SHA-1: `DD:49:CA:8A:12:8F:9D:A3:B5:F7:18:F9:EF:9F:77:B4:C3:30:87:EC`
5. Abre RodaLog → **Más → Ajustes → Conectar cuenta de Google** → **Crear hoja de cálculo nueva**.

> La firma usa `app/rodalog.keystore` (incluida en el repo para que el APK siempre tenga la misma huella).
> Si prefieres tu propia llave, reemplázala y actualiza la SHA-1.

### Compartir con otra persona

1. En Ajustes toca **Compartir con familia** (o abre la hoja en Google Sheets → *Compartir* → Editor).
2. La otra persona instala el APK, conecta su cuenta de Google (debe estar en usuarios de prueba) y pega el enlace en **Conectar hoja existente**.

## Viajes automáticos: cómo configurarlo

1. **Vehículos →** edita tu vehículo → **Vincular dispositivo Bluetooth** (estéreo del auto, casco, manos libres…; debe estar emparejado con el teléfono).
2. **Ajustes → Viajes automáticos**: concede *ubicación precisa*, *ubicación “todo el tiempo”*, *dispositivos cercanos* y *notificaciones*, y quita la restricción de batería.
3. Al subir al vehículo y conectarse el Bluetooth la app queda **armada**; al superar ~8 km/h empieza a grabar.
   Termina sola al desconectarse el Bluetooth (2 min de gracia) o tras 10 min detenido. También hay botón manual **Iniciar viaje**.

Al terminar un viaje se suma al odómetro y se descuenta del tanque. El consumo mostrado es una **estimación**
(promedio de tus últimas cargas con tanque lleno); se recalibra cada vez que registras una carga completa.

## Estructura de la hoja

Pestañas `Vehiculos`, `Combustible`, `Gastos`, `Recordatorios`, `Viajes`. Puedes editar filas a mano:
los cambios se bajan a la app en la siguiente sincronización (gana el registro más reciente).

## Compilar

```bash
gradle :app:assembleRelease      # Gradle 8.10+, JDK 17, Android SDK 35
```

Pila: Kotlin, Jetpack Compose (Material 3), Room, WorkManager, DataStore, Fused Location, Google Identity Authorization + Sheets REST API.
