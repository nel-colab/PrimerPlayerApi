-- Desde esta versión los eventos de identidad (UsuarioRegistrado, VerificacionSolicitada, RecuperacionSolicitada) ya
-- no llevan el token en claro: notificaciones lo emite al enviar el correo. Las publicaciones anteriores que sigan en
-- el outbox (pendientes por un SMTP caído, o archivadas) guardan el token en serialized_event y además ya no se
-- pueden leer con el formato nuevo del evento: se borran. Quien no alcanzó a recibir el correo puede pedirlo de
-- nuevo ("Reenviar verificación" o "¿Olvidaste tu contraseña?").

DELETE FROM event_publication
WHERE event_type IN ('cl.primerplayer.identidad.events.UsuarioRegistrado',
                     'cl.primerplayer.identidad.events.VerificacionSolicitada',
                     'cl.primerplayer.identidad.events.RecuperacionSolicitada');

DELETE FROM event_publication_archive
WHERE event_type IN ('cl.primerplayer.identidad.events.UsuarioRegistrado',
                     'cl.primerplayer.identidad.events.VerificacionSolicitada',
                     'cl.primerplayer.identidad.events.RecuperacionSolicitada');
