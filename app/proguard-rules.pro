# GeckoView ya incluye sus propias consumer rules; este keep es un refuerzo
# para que R8 no elimine clases a las que Gecko accede por reflexión.
-keep class org.mozilla.geckoview.** { *; }
-dontwarn org.mozilla.geckoview.**

# SnakeYAML (transitivo) referencia java.beans.*, que no existe en Android.
# Son rutas de escritorio que nunca se ejecutan aquí; R8 full mode las marca
# como 'missing classes'. -dontwarn las suprime sin afectar al runtime.
-dontwarn java.beans.**
-dontwarn org.yaml.snakeyaml.**
