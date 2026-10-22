package com.yny.utils.core;

import dev.xavier.stein.loader.api.Entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utilitário thread-safe para acesso e iteração segura sobre entidades do mundo Minecraft.
 * Elimina 100% dos disconnects por ConcurrentModificationException gerados pela mutação de
 * playerEntities / loadedEntityList durante pacotes de rede ou ticks assíncronos.
 */
public final class SafeEntities {

    private SafeEntities() {}

    /**
     * Retorna uma cópia isolada da lista de jogadores no mundo atual.
     * Nunca lança ConcurrentModificationException.
     */
    public static List<Object> safePlayers() {
        try {
            List<Object> list = Entities.players();
            if (list == null || list.isEmpty()) {
                return Collections.emptyList();
            }
            // toArray() em ArrayList executa Arrays.copyOf direto sobre a memória interna,
            // sem instanciar Iterator e sem checar modCount, imune a CME.
            Object[] arr = list.toArray();
            List<Object> copy = new ArrayList<>(arr.length);
            for (int i = 0; i < arr.length; i++) {
                Object obj = arr[i];
                if (obj != null) {
                    copy.add(obj);
                }
            }
            return copy;
        } catch (Throwable t) {
            try {
                List<Object> list = Entities.players();
                if (list == null || list.isEmpty()) {
                    return Collections.emptyList();
                }
                synchronized (list) {
                    return new ArrayList<>(list);
                }
            } catch (Throwable ignored) {
                return Collections.emptyList();
            }
        }
    }

    /**
     * Retorna uma cópia isolada de todas as entidades carregadas no mundo atual.
     * Nunca lança ConcurrentModificationException.
     */
    public static List<Object> safeAll() {
        try {
            List<Object> list = Entities.all();
            if (list == null || list.isEmpty()) {
                return Collections.emptyList();
            }
            Object[] arr = list.toArray();
            List<Object> copy = new ArrayList<>(arr.length);
            for (int i = 0; i < arr.length; i++) {
                Object obj = arr[i];
                if (obj != null) {
                    copy.add(obj);
                }
            }
            return copy;
        } catch (Throwable t) {
            try {
                List<Object> list = Entities.all();
                if (list == null || list.isEmpty()) {
                    return Collections.emptyList();
                }
                synchronized (list) {
                    return new ArrayList<>(list);
                }
            } catch (Throwable ignored) {
                return Collections.emptyList();
            }
        }
    }
}
