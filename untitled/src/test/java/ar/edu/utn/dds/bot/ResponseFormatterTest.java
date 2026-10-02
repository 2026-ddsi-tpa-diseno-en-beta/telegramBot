package ar.edu.utn.dds.bot;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;

class ResponseFormatterTest {
    @Test void preservesReferencesStatesAndQuantitiesWithoutInventingDelivery() {
        String result = ResponseFormatter.pages("{\"id\":\"full-uuid\",\"estado\":\"INGRESADA\",\"cantidad\":10}").get(0);
        assertTrue(result.contains("Referencia: full-uuid"));
        assertTrue(result.contains("Estado: INGRESADA"));
        assertTrue(result.contains("Cantidad: 10"));
        assertFalse(result.contains("entregada"));
    }
    @Test void paginationPreservesAllDataIncludingSurrogatePairs() {
        String data = "[{\"descripcion\":\"" + "🤝".repeat(4000) + "\",\"id\":\"last-id\"}]";
        var pages = ResponseFormatter.pages(data);
        assertTrue(pages.size() > 1);
        assertTrue(String.join("", pages).contains("last-id"));
        for (String page : pages) {
            assertTrue(page.length() < 3000);
            assertFalse(Character.isHighSurrogate(page.charAt(page.length() - 1)));
        }
    }
    @Test void configuredAdminRestrictionDoesNotChangeInitialRoleSelection() {
        var handler = new BotCommandHandler(new DonadoresApiClient("http://localhost:1"), null, Set.of(1L));
        assertTrue(handler.handle(2,"/start").contains("/donador"));
        assertTrue(handler.handle(2,"/admin").contains("no está habilitado"));
        assertTrue(handler.handle(1,"/admin").contains("Rol seleccionado: ADMIN"));
    }
    @Test void emptyResultsAndErrorsAreActionable() {
        assertTrue(ResponseFormatter.pages("[]").get(0).contains("No hay resultados"));
        assertTrue(ResponseFormatter.error(409,"trace-test").contains("estado actual"));
        assertTrue(ResponseFormatter.error(409,"trace-test").contains("trace-test"));
    }
}
