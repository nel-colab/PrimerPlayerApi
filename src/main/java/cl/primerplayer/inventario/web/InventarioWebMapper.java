package cl.primerplayer.inventario.web;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import cl.primerplayer.inventario.api.AjustarStockUseCase.StockActual;
import cl.primerplayer.inventario.web.dto.InventarioDtos.StockResponse;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface InventarioWebMapper {

    StockResponse toResponse(StockActual stock);
}
