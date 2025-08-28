package pe.edu.vallegrande.ms_water_quality;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import pe.edu.vallegrande.ms_water_quality.application.services.impl.TestingPointServiceImpl;
import pe.edu.vallegrande.ms_water_quality.domain.enums.Constants;
import pe.edu.vallegrande.ms_water_quality.domain.models.TestingPoint;
import pe.edu.vallegrande.ms_water_quality.infrastructure.dto.request.TestingPointCreateRequest;
import pe.edu.vallegrande.ms_water_quality.infrastructure.repository.TestingPointRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TestingPointServiceImplTest {

    @Mock
    private TestingPointRepository repository;

    @InjectMocks
    private TestingPointServiceImpl service;

    private TestingPoint samplePoint;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        samplePoint = new TestingPoint();
        samplePoint.setId("123");
        samplePoint.setPointCode("PM001");
        samplePoint.setPointName("Reservorio Central");
        samplePoint.setOrganizationId("ORG1");
        samplePoint.setStatus(Constants.ACTIVE.name());
        samplePoint.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void testSaveValidRequest() {
        TestingPointCreateRequest request = new TestingPointCreateRequest();
        request.setOrganizationId("ORG1");
        request.setPointName("Nuevo Punto");

        // Inicializa coordinates
        TestingPointCreateRequest.Coordinates coords = new TestingPointCreateRequest.Coordinates();
        coords.setLatitude(-12.05);
        coords.setLongitude(-77.05);
        request.setCoordinates(coords);

        when(repository.findAll()).thenReturn(Flux.empty());
        when(repository.save(any(TestingPoint.class))).thenReturn(Mono.just(samplePoint));

        StepVerifier.create(service.save(request))
                .expectNextMatches(resp -> resp.getPointCode().equals("PM001"))
                .verifyComplete();

        System.out.println("ESCENARIO 01: POST = Creación exitosa");
    }

    @Test
    void testGetAll() {
        when(repository.findAll()).thenReturn(Flux.just(samplePoint));

        StepVerifier.create(service.getAll())
                .expectNext(samplePoint)
                .verifyComplete();

        System.out.println("ESCENARIO 02: GET = Consulta exitosa");
    }

    @Test
    void testGetByIdFound() {
        when(repository.findById("123")).thenReturn(Mono.just(samplePoint));

        StepVerifier.create(service.getById("123"))
                .expectNextMatches(tp -> tp.getPointName().equals("Reservorio Central"))
                .verifyComplete();

        System.out.println("ESCENARIO 03: GET by ID = Consulta exitosa");
    }

    @Test
    void testUpdateExisting() {
        samplePoint.setPointName("Reservorio Actualizado");

        when(repository.findById("123")).thenReturn(Mono.just(samplePoint));
        when(repository.findAll()).thenReturn(Flux.just(samplePoint)); // 👈 mock obligatorio
        when(repository.save(any(TestingPoint.class))).thenReturn(Mono.just(samplePoint));

        StepVerifier.create(service.update("123", samplePoint))
                .expectNextMatches(tp -> tp.getPointName().equals("Reservorio Actualizado"))
                .verifyComplete();

        System.out.println("ESCENARIO 04: PUT = Actualización exitosa");
    }

    @Test
    void testDeleteExisting() {
        when(repository.findById("123")).thenReturn(Mono.just(samplePoint));
        when(repository.delete(samplePoint)).thenReturn(Mono.empty());

        StepVerifier.create(service.delete("123"))
                .verifyComplete();

        System.out.println("ESCENARIO 05: DELETE = Eliminación exitosa");
    }
}
