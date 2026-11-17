package cl.primerplayer.identidad.web;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import cl.primerplayer.identidad.api.IniciarSesionUseCase.SesionIniciada;
import cl.primerplayer.identidad.api.RegistrarUsuarioUseCase.RegistrarUsuarioCommand;
import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.identidad.api.UsuarioResumen;
import cl.primerplayer.identidad.web.dto.LoginResponse;
import cl.primerplayer.identidad.web.dto.RegistroRequest;
import cl.primerplayer.identidad.web.dto.UsuarioResponse;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface IdentidadWebMapper {

    RegistrarUsuarioCommand toCommand(RegistroRequest request);

    @Mapping(target = "name", source = "nombre")
    @Mapping(target = "role", source = "rol")
    UsuarioResponse toResponse(UsuarioResumen usuario);

    @Mapping(target = "user", source = "usuario")
    LoginResponse toResponse(SesionIniciada sesion);

    default String rolFrontend(Rol rol) {
        return rol.frontend();
    }
}
