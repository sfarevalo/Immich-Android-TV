root@PipiUnraid:~# cat replace_ac3_aac.sh 
#!/bin/bash

# Configuración
INPUT_FILE="/root/rutas_completas.txt"
BACKUP_DIR="/mnt/user/photos_immich/backup_ac3_originals"
LOG_FILE="/root/replacement_log.txt"
TEMP_DIR="/tmp/immich_conversion"

# Crear directorios necesarios
mkdir -p "$BACKUP_DIR"
mkdir -p "$TEMP_DIR"

# Limpiar log anterior
> "$LOG_FILE"

echo "========================================="
echo "Reemplazo AC3 -> AAC (in-place)"
echo "========================================="
echo ""
echo "⚠️  IMPORTANTE: Este script reemplazará los archivos originales"
echo "    Los originales se guardarán en: $BACKUP_DIR"
echo ""
read -p "¿Continuar? (escribe SI en mayúsculas): " confirmacion

if [[ "$confirmacion" != "SI" ]]; then
    echo "Operación cancelada."
    exit 0
fi

echo ""
echo "Iniciando proceso..."
echo ""

# Contadores
total=0
exitosos=0
fallidos=0

# Leer cada línea del archivo
while IFS= read -r linea; do
    # Saltar líneas vacías
    [[ -z "$linea" ]] && continue
    
    # Extraer la ruta (antes del ->)
    ruta=$(echo "$linea" | awk -F' -> ' '{print $1}' | xargs)
    nombre_info=$(echo "$linea" | awk -F' -> ' '{print $2}')
    
    # Verificar que el archivo existe
    if [[ ! -f "$ruta" ]]; then
        echo "⚠ ADVERTENCIA: No se encuentra el archivo: $ruta"
        echo "ERROR: Archivo no encontrado: $ruta" >> "$LOG_FILE"
        fallidos=$((fallidos + 1))
        continue
    fi
    
    total=$((total + 1))
    
    # Obtener información del archivo
    nombre_archivo=$(basename "$ruta")
    ruta_dir=$(dirname "$ruta")
    nombre_sin_ext="${nombre_archivo%.*}"
    extension="${nombre_archivo##*.}"
    archivo_temp="$TEMP_DIR/${nombre_sin_ext}_temp.${extension}"
    
    echo "========================================="
    echo "[$total] Procesando: $nombre_info"
    echo "    Archivo: $nombre_archivo"
    echo "    Ruta: $ruta"
    echo ""
    
    # Verificar que es realmente AC3
    codec_actual=$(ffprobe -v error -select_streams a:0 -show_entries stream=codec_name -of default=noprint_wrappers=1:nokey=1 "$ruta" 2>/dev/null)
    
    if [[ "$codec_actual" != "ac3" ]]; then
        echo "⚠ ADVERTENCIA: El archivo NO tiene codec AC3 (tiene: $codec_actual)"
        echo "   Saltando este archivo..."
        echo "SALTADO: $ruta - Codec actual: $codec_actual" >> "$LOG_FILE"
        echo ""
        continue
    fi
    
    echo "✓ Confirmado codec AC3"
    
    # Detectar el start_time del video para corregir el offset
    start_time=$(ffprobe -v error -select_streams v:0 -show_entries stream=start_time -of default=noprint_wrappers=1:nokey=1 "$ruta" 2>/dev/null)
    
    if [[ -z "$start_time" ]]; then
        start_time="0.000000"
    fi
    
    echo "→ Start time detectado: $start_time"
    
    # Guardar permisos y fechas originales
    permisos=$(stat -c %a "$ruta" 2>/dev/null || stat -f %A "$ruta")
    
    echo "✓ Guardando metadatos originales"
    
    # Paso 1: Hacer backup del original
    backup_path="$BACKUP_DIR/$nombre_archivo"
    echo "→ Creando backup en: $backup_path"
    
    if ! cp -p "$ruta" "$backup_path"; then
        echo "✗ ERROR: No se pudo crear el backup"
        echo "ERROR backup: $ruta" >> "$LOG_FILE"
        fallidos=$((fallidos + 1))
        echo ""
        continue
    fi
    
    echo "✓ Backup creado exitosamente"
    
    # Paso 2: Convertir a AAC estéreo con corrección de offset
    echo "→ Convirtiendo a AAC estéreo (corrigiendo offset de tiempo)..."
    
    if ffmpeg -itsoffset -${start_time} \
        -i "$ruta" \
        -i "$ruta" \
        -map 0:v \
        -map 1:a \
        -c:v copy \
        -c:a aac \
        -b:a 192k \
        -ac 2 \
        -movflags +faststart \
        -y \
        "$archivo_temp" \
        2>> "$LOG_FILE"; then
        
        echo "✓ Conversión completada"
    else
        echo "✗ ERROR en la conversión"
        echo "ERROR conversión: $ruta" >> "$LOG_FILE"
        fallidos=$((fallidos + 1))
        rm -f "$archivo_temp"
        echo ""
        continue
    fi
    
    # Paso 3: Verificar que la conversión fue exitosa
    codec_nuevo=$(ffprobe -v error -select_streams a:0 -show_entries stream=codec_name -of default=noprint_wrappers=1:nokey=1 "$archivo_temp" 2>/dev/null)
    canales=$(ffprobe -v error -select_streams a:0 -show_entries stream=channels -of default=noprint_wrappers=1:nokey=1 "$archivo_temp" 2>/dev/null)
    
    if [[ "$codec_nuevo" != "aac" ]] || [[ "$canales" != "2" ]]; then
        echo "✗ ERROR: La conversión no produjo AAC estéreo"
        echo "   Codec: $codec_nuevo, Canales: $canales"
        echo "ERROR verificación: $ruta - Codec: $codec_nuevo, Canales: $canales" >> "$LOG_FILE"
        fallidos=$((fallidos + 1))
        rm -f "$archivo_temp"
        echo ""
        continue
    fi
    
    echo "✓ Verificado: AAC estéreo correcto"
    
    # Paso 4: Reemplazar el archivo original
    echo "→ Reemplazando archivo original..."
    
    if ! mv -f "$archivo_temp" "$ruta"; then
        echo "✗ ERROR: No se pudo reemplazar el archivo"
        echo "   El original está seguro en: $backup_path"
        echo "   El archivo convertido está en: $archivo_temp"
        echo "ERROR reemplazo: $ruta" >> "$LOG_FILE"
        fallidos=$((fallidos + 1))
        echo ""
        continue
    fi
    
    echo "✓ Archivo reemplazado"
    
    # Paso 5: Cambiar propietario a nobody:users (requerido por Immich)
    echo "→ Ajustando propietario del archivo..."
    chown nobody:users "$ruta"
    
    # Paso 6: Restaurar permisos y fechas
    chmod "$permisos" "$ruta" 2>/dev/null
    touch -r "$backup_path" "$ruta"
    
    echo "✓ Permisos y fechas restaurados"
    
    # Paso 7: Buscar y eliminar archivo transcodificado si existe
    echo "→ Buscando archivo transcodificado..."
    
    # Obtener el asset ID de este archivo
    asset_id=$(docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -t -A -c \
        "SELECT id FROM asset WHERE \"originalPath\" = '$ruta_immich';" 2>/dev/null | xargs)
    
    if [[ -n "$asset_id" ]]; then
        # Buscar si tiene encodedVideoPath
        encoded_path=$(docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -t -A -c \
            "SELECT \"encodedVideoPath\" FROM asset WHERE id = '$asset_id';" 2>/dev/null | xargs)
        
        if [[ -n "$encoded_path" && "$encoded_path" != "" ]]; then
            # Convertir ruta de /photos a /mnt/user/photos_immich
            encoded_file=$(echo "$encoded_path" | sed 's|^/photos|/mnt/user/photos_immich|')
            
            if [[ -f "$encoded_file" ]]; then
                echo "  → Borrando archivo transcodificado: $encoded_file"
                rm -f "$encoded_file"
                
                # Limpiar el campo en la base de datos
                docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -c \
                    "UPDATE asset SET \"encodedVideoPath\" = '' WHERE id = '$asset_id';" >> "$LOG_FILE" 2>&1
                
                echo "  ✓ Archivo transcodificado eliminado - Immich lo regenerará"
            else
                echo "  → Archivo transcodificado no existe en disco"
            fi
        else
            echo "  → No hay archivo transcodificado"
        fi
    fi
    
    # Registro de éxito
    echo "ÉXITO: $ruta" >> "$LOG_FILE"
    exitosos=$((exitosos + 1))
    
    echo ""
    echo "✅ COMPLETADO EXITOSAMENTE"
    echo ""
    
done < "$INPUT_FILE"

# Limpiar directorio temporal
rm -rf "$TEMP_DIR"

echo "========================================="
echo "RESUMEN FINAL"
echo "========================================="
echo "Total de archivos procesados: $total"
echo "Reemplazos exitosos: $exitosos"
echo "Fallos: $fallidos"
echo ""
echo "Backups guardados en: $BACKUP_DIR"
echo "Log completo en: $LOG_FILE"
echo "========================================="
echo ""

if [[ $exitosos -gt 0 ]]; then
    echo "✅ Los archivos han sido reemplazados exitosamente."
    echo "   Immich debería detectar los cambios automáticamente."
    echo ""
    echo "💡 Si los videos no se reproducen inmediatamente en Immich:"
    echo "   1. Espera unos minutos para que se regeneren los thumbnails"
    echo "   2. Intenta forzar la regeneración desde Immich admin"
    echo "   3. Los backups están disponibles en $BACKUP_DIR"
fi
root@PipiUnraid:~# 
