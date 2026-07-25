package com.example.parser

import com.example.model.Channel
import java.util.UUID

object M3uParser {

    /**
     * Parsing helper that extracts an attribute value from a standard M3U line (e.g. tvg-logo="https://...")
     */
    private fun extractAttribute(line: String, attrName: String): String {
        val key = "$attrName=\""
        val startIndex = line.indexOf(key)
        if (startIndex == -1) return ""
        val valueStart = startIndex + key.length
        val endIndex = line.indexOf("\"", valueStart)
        if (endIndex == -1) return ""
        return line.substring(valueStart, endIndex).trim()
    }

    /**
     * Smart classification of channels to populate multiple horizontal lists.
     * Fallbacks to the playlist's "group-title" if specified.
     */
    fun classifyChannel(name: String, groupTitle: String): String {
        val upperName = name.uppercase()
        return when {
            upperName.contains("ESPN PREMIUM") -> "Deportes Premium"
            upperName.contains("ESPN") || 
            upperName.contains("DEPORTV") || 
            upperName.contains("SPORT") -> "Deportes"
            upperName.contains("A24") || 
            upperName.contains("C5N") || 
            upperName.contains("TN NOTICIAS") || 
            upperName.contains("TN") || 
            upperName.contains("CRONICA") || 
            upperName.contains("CRÓNICA") || 
            upperName.contains("LA NACIÓN") || 
            upperName.contains("LA NACION") || 
            upperName.contains("LN+") || 
            upperName.contains("CANAL 26") -> "Noticias"
            upperName.contains("TELEFE") || 
            upperName.contains("AMERICA") || 
            upperName.contains("EL NUEVE") || 
            upperName.contains("EL TRECE") || 
            upperName.contains("TV PUBLICA") || 
            upperName.contains("TV PÚBLICA") || 
            upperName.contains("NET TV") -> "Canales de Aire"
            else -> {
                if (groupTitle.isNotEmpty() && groupTitle != "Argentina") {
                    groupTitle
                } else {
                    "Locales y Variedades"
                }
            }
        }
    }

    /**
     * Parses the raw M3U text content.
     */
    fun parse(m3uContent: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = m3uContent.lines()
        
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF:")) {
                // Parse attributes
                val tvgLogo = extractAttribute(line, "tvg-logo")
                val groupTitle = extractAttribute(line, "group-title")
                var tvgName = extractAttribute(line, "tvg-name")
                
                // Fallback to name after comma
                val commaIndex = line.lastIndexOf(",")
                val fallbackName = if (commaIndex != -1 && commaIndex < line.length - 1) {
                    line.substring(commaIndex + 1).trim()
                } else {
                    ""
                }
                
                val finalName = if (tvgName.isNotEmpty()) tvgName else if (fallbackName.isNotEmpty()) fallbackName else "Canal desconocido"
                
                // Look for the URL line which follows
                var streamUrl = ""
                var j = i + 1
                while (j < lines.size) {
                    val nextLine = lines[j].trim()
                    if (nextLine.isNotEmpty()) {
                        if (nextLine.startsWith("#")) {
                            // If it starts with another tag, we missed the url or it's empty
                            break
                        } else {
                            streamUrl = nextLine
                            i = j // Advance main loop to this line
                            break
                        }
                    }
                    j++
                }
                
                if (streamUrl.isNotEmpty()) {
                    val assignedCategory = if (groupTitle.trim().isNotEmpty()) groupTitle.trim() else "Otros"
                    channels.add(
                        Channel(
                            id = UUID.nameUUIDFromBytes(streamUrl.toByteArray()).toString(),
                            name = finalName,
                            logoUrl = tvgLogo,
                            category = assignedCategory,
                            streamUrl = streamUrl,
                            groupTitle = groupTitle
                        )
                    )
                }
            }
            i++
        }
        return channels
    }

    /**
     * Preloaded live Argentine M3U list provided directly by you
     */
    val DEFAULT_M3U_LIST = """
#EXTINF:-1 tvg-id="" tvg-name="A24 | AR" tvg-logo="https://i.postimg.cc/VL2YNmHh/images-q-tbn-ANd9Gc-T8yf-XBu-Qmon9WVy3ETX9fuq0w4U8Hvq391YA-s.png" group-title="Argentina",A24 | AR
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712701.ts
#EXTINF:-1 tvg-id="" tvg-name="A24 FHD" tvg-logo="https://i.postimg.cc/VL2YNmHh/images-q-tbn-ANd9Gc-T8yf-XBu-Qmon9WVy3ETX9fuq0w4U8Hvq391YA-s.png" group-title="Argentina",A24 FHD
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712702.ts
#EXTINF:-1 tvg-id="" tvg-name="America TV | AR" tvg-logo="https://i.postimg.cc/k4MCH4Dh/America-TV-(Nuevo-logo-Junio-2020).png" group-title="Argentina",America TV | AR
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712703.ts
#EXTINF:-1 tvg-id="" tvg-name="America TV FHD" tvg-logo="https://i.postimg.cc/k4MCH4Dh/America-TV-(Nuevo-logo-Junio-2020).png" group-title="Argentina",America TV FHD
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712704.ts
#EXTINF:-1 tvg-id="" tvg-name="C5N FHD| AR" tvg-logo="https://i.postimg.cc/gjQPrH4v/C5N-Logo-2015.png" group-title="Argentina",C5N FHD| AR
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712705.ts
#EXTINF:-1 tvg-id="" tvg-name="CRONICA" tvg-logo="https://i.postimg.cc/cJ0GKH76/Cronica-TV-logotipo-(2016).png" group-title="Argentina",CRONICA
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712706.ts
#EXTINF:-1 tvg-id="" tvg-name="Crónica TV  HD| AR" tvg-logo="https://i.postimg.cc/cJ0GKH76/Cronica-TV-logotipo-(2016).png" group-title="Argentina",Crónica TV  HD| AR
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712707.ts
#EXTINF:-1 tvg-id="" tvg-name="El Nueve" tvg-logo="https://i.postimg.cc/MT3CrmCs/Canal-9-ba-logo.png" group-title="Argentina",El Nueve
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712708.ts
#EXTINF:-1 tvg-id="" tvg-name="El Nueve HD" tvg-logo="https://i.postimg.cc/MT3CrmCs/Canal-9-ba-logo.png" group-title="Argentina",El Nueve HD
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712709.ts
#EXTINF:-1 tvg-id="" tvg-name="El Trece" tvg-logo="https://i.postimg.cc/HWRGw34L/Logo-Canal-13-200-8.png" group-title="Argentina",El Trece
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712710.ts
#EXTINF:-1 tvg-id="" tvg-name="El Trece FHD" tvg-logo="https://i.postimg.cc/HWRGw34L/Logo-Canal-13-200-8.png" group-title="Argentina",El Trece FHD
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712711.ts
#EXTINF:-1 tvg-id="" tvg-name="La Nación + | AR" tvg-logo="https://i.postimg.cc/mZvLLTn0/f500x333-807353-831156-5050.jpg" group-title="Argentina",La Nación + | AR
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712712.ts
#EXTINF:-1 tvg-id="" tvg-name="La Nación + HD" tvg-logo="https://i.postimg.cc/mZvLLTn0/f500x333-807353-831156-5050.jpg" group-title="Argentina",La Nación + HD
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712713.ts
#EXTINF:-1 tvg-id="" tvg-name="TELEFE" tvg-logo="https://i.postimg.cc/x8MSJbZX/330px-Telefe-(nuevo-logo).png" group-title="Argentina",TELEFE
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712714.ts
#EXTINF:-1 tvg-id="" tvg-name="Telefe FHD" tvg-logo="https://i.postimg.cc/x8MSJbZX/330px-Telefe-(nuevo-logo).png" group-title="Argentina",Telefe FHD
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712715.ts
#EXTINF:-1 tvg-id="" tvg-name="TN Noticias" tvg-logo="https://i.postimg.cc/tTnKwx9g/250px-TN-todo-noticias-logo-svg.png" group-title="Argentina",TN Noticias
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712717.ts
#EXTINF:-1 tvg-id="" tvg-name="TN NOTICIAS" tvg-logo="https://i.postimg.cc/tTnKwx9g/250px-TN-todo-noticias-logo-svg.png" group-title="Argentina",TN NOTICIAS
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712718.ts
#EXTINF:-1 tvg-id="" tvg-name="TV Publica" tvg-logo="https://i.postimg.cc/hPBkZvwY/1280px-TVP-Television-Publica-(2021)-svg.png" group-title="Argentina",TV Publica
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712719.ts
#EXTINF:-1 tvg-id="" tvg-name="TV Publica FHD" tvg-logo="https://i.postimg.cc/hPBkZvwY/1280px-TVP-Television-Publica-(2021)-svg.png" group-title="Argentina",TV Publica FHD
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712720.ts
#EXTINF:-1 tvg-id="" tvg-name="ARGENTINISIMA" tvg-logo="https://i.postimg.cc/fbnqyrs4/latest-(20).png" group-title="Argentina",ARGENTINISIMA
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712721.ts
#EXTINF:-1 tvg-id="" tvg-name="Canal 13 La Rioja" tvg-logo="https://i.postimg.cc/WbxXYHh4/latest-(21).png" group-title="Argentina",Canal 13 La Rioja
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712723.ts
#EXTINF:-1 tvg-id="" tvg-name="Canal 26 | AR" tvg-logo="https://i.postimg.cc/3Rbg4kG3/images-q-tbn-ANd9Gc-Se0i-OYk-MVUv8b-RSmev324PVm-Iah3Lbnmk-a-A-s.png" group-title="Argentina",Canal 26 | AR
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712724.ts
#EXTINF:-1 tvg-id="" tvg-name="Canal 26 FHD" tvg-logo="https://i.postimg.cc/3Rbg4kG3/images-q-tbn-ANd9Gc-Se0i-OYk-MVUv8b-RSmev324PVm-Iah3Lbnmk-a-A-s.png" group-title="Argentina",Canal 26 FHD
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712725.ts
#EXTINF:-1 tvg-id="" tvg-name="Canal 4 Posadas" tvg-logo="https://canalcuatroposadas.com.ar/wp-content/uploads/2024/02/logo-web.png" group-title="Argentina",Canal 4 Posadas
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712726.ts
#EXTINF:-1 tvg-id="" tvg-name="Canal 7 Jujuy" tvg-logo="https://static.wikia.nocookie.net/logopedia/images/1/1f/514px-Canal_Siete_Jujuy_%28Logo_2015%29.png/revision/latest?cb=20191126073719" group-title="Argentina",Canal 7 Jujuy
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712727.ts
#EXTINF:-1 tvg-id="" tvg-name="Canal 9 Litoral" tvg-logo="https://static.wikia.nocookie.net/logopedia/images/d/d5/Canal_9_Litoral_(Logo_2021).png/revision/latest?cb=20220305163041" group-title="Argentina",Canal 9 Litoral
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712728.ts
#EXTINF:-1 tvg-id="" tvg-name="13 Max HD" tvg-logo="https://i.ibb.co/sbHJbrC/13maxhd.png" group-title="Argentina",13 Max HD
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712730.ts
#EXTINF:-1 tvg-id="" tvg-name="CIUDAD MAGAZINE HD" tvg-logo="https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcS5-0odv_2wgxZHAqMfic5Q9Qh9eKutgZ3F0A&s" group-title="Argentina",CIUDAD MAGAZINE HD
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712732.ts
#EXTINF:-1 tvg-id="" tvg-name="5RTV Santa Fe" tvg-logo="https://i.ibb.co/W6xkB9w/santafe.png" group-title="Argentina",5RTV Santa Fe
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712733.ts
#EXTINF:-1 tvg-id="" tvg-name="Multivision HD" tvg-logo="https://play-lh.googleusercontent.com/4KefEkBSbET6Imw1iLMrMd70mG4LP_5b6faSohDm6eKUipnx_ApRrQXAPMaLguA0eoI" group-title="Argentina",Multivision HD
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712734.ts
#EXTINF:-1 tvg-id="" tvg-name="Neo TV" tvg-logo="https://static.wikia.nocookie.net/logopedia/images/1/1a/Neo_TV_Florencio_Varela.png/revision/latest/scale-to-width-down/1000?cb=20250702200816" group-title="Argentina",Neo TV
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712735.ts
#EXTINF:-1 tvg-id="" tvg-name="NET TV" tvg-logo="https://static.wikia.nocookie.net/logopedia/images/2/21/NETTV2018.png/revision/latest/scale-to-width-down/1000?cb=20210315130135&path-prefix=es" group-title="Argentina",NET TV
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712736.ts
#EXTINF:-1 tvg-id="" tvg-name="Paka Paka HD" tvg-logo="https://i.postimg.cc/WbHjtTzQ/Paka-Paka-2025.png" group-title="Argentina",Paka Paka HD
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712737.ts
#EXTINF:-1 tvg-id="" tvg-name="RTN Neuquén" tvg-logo="https://directostv.teleame.com/wp-content/uploads/2025/03/Canal-RTN-Neuquen-en-VIVO-Online.png" group-title="Argentina",RTN Neuquén
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712739.ts
#EXTINF:-1 tvg-id="" tvg-name="Telemax" tvg-logo="https://static.wikia.nocookie.net/logopedia/images/2/29/08%29Telemax_%282018%29.png/revision/latest?cb=20220113152551&path-prefix=es" group-title="Argentina",Telemax
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712740.ts
#EXTINF:-1 tvg-id="" tvg-name="Volver HD" tvg-logo="https://static.wikia.nocookie.net/logopedia/images/0/0b/Volver_2016.png/revision/latest?cb=20210525002558&path-prefix=es" group-title="Argentina",Volver HD
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712741.ts
#EXTINF:-1 tvg-id="" tvg-name="Deportv | AR FHD" tvg-logo="https://static.wikia.nocookie.net/logopedia/images/0/0e/DeporTV_%282020%29.png/revision/latest/scale-to-width-down/1000?cb=20210101172130&path-prefix=es" group-title="Argentina",Deportv | AR FHD
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712742.ts
#EXTINF:-1 tvg-id="" tvg-name="ESPN FHD | AR" tvg-logo="https://r2.thesportsdb.com/images/media/channel/logo/hdrkql1660762351.png" group-title="Argentina",ESPN FHD | AR
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712743.ts
#EXTINF:-1 tvg-id="" tvg-name="ESPN 2  FHD | AR" tvg-logo="https://r2.thesportsdb.com/images/media/channel/logo/wcualh1660760950.png" group-title="Argentina",ESPN 2  FHD | AR
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712744.ts
#EXTINF:-1 tvg-id="" tvg-name="ESPN 3 HD| AR" tvg-logo="https://r2.thesportsdb.com/images/media/channel/logo/wkdrnd1660760961.png" group-title="Argentina",ESPN 3 HD| AR
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712745.ts
#EXTINF:-1 tvg-id="" tvg-name="ESPN 4 HD | AR" tvg-logo="https://static.wikia.nocookie.net/logopedia/images/3/30/ESPN_4.svg/revision/latest/scale-to-width-down/250?cb=20211120194330" group-title="Argentina",ESPN 4 HD | AR
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712746.ts
#EXTINF:-1 tvg-id="" tvg-name="ESPN Premium  | AR FHD" tvg-logo="https://upload.wikimedia.org/wikipedia/commons/d/db/ESPN_Premium_logo.png" group-title="Argentina",ESPN Premium  | AR FHD
http://vivotv.site:80/LuisCalderon/HVKQHgZbhS/712747.ts
        """.trimIndent()
}
