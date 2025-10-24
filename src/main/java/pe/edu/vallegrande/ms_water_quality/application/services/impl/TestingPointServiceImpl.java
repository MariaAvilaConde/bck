package pe.edu.vallegrande.ms_water_quality.application.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.edu.vallegrande.ms_water_quality.application.services.TestingPointService;
import pe.edu.vallegrande.ms_water_quality.domain.models.TestingPoint;
import pe.edu.vallegrande.ms_water_quality.infrastructure.client.dto.ExternalOrganization;
import pe.edu.vallegrande.ms_water_quality.infrastructure.dto.request.TestingPointCreateRequest;
import pe.edu.vallegrande.ms_water_quality.infrastructure.dto.response.TestingPointResponse;
import pe.edu.vallegrande.ms_water_quality.infrastructure.dto.response.enriched.TestingPointEnrichedResponse;
import pe.edu.vallegrande.ms_water_quality.infrastructure.exception.CustomException;
import pe.edu.vallegrande.ms_water_quality.infrastructure.repository.TestingPointRepository;
import pe.edu.vallegrande.ms_water_quality.infrastructure.service.ExternalServiceClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TestingPointServiceImpl implements TestingPointService {

    private final TestingPointRepository testingPointRepository;
    private final ExternalServiceClient externalServiceClient;

    @Override
    public Flux<TestingPointEnrichedResponse> getAll() {
        return getCurrentUserOrganizationId()
            .flatMapMany(this::getAllByOrganization);
    }

    @Override
    public Flux<TestingPointEnrichedResponse> getAllActive() {
        return getCurrentUserOrganizationId()
            .flatMapMany(this::getAllActiveByOrganization);
    }

    @Override
    public Flux<TestingPointEnrichedResponse> getAllInactive() {
        return getCurrentUserOrganizationId()
            .flatMapMany(this::getAllInactiveByOrganization);
    }

    @Override
    public Mono<TestingPointEnrichedResponse> getById(String id) {
        return getCurrentUserOrganizationId()
            .flatMap(orgId -> getByIdAndOrganization(id, orgId));
    }

    @Override
    public Mono<TestingPointResponse> save(TestingPointCreateRequest request) {
        TestingPoint testingPoint = new TestingPoint();
        testingPoint.setOrganizationId(request.getOrganizationId());
        
        if (request.getPointCode() != null && !request.getPointCode().trim().isEmpty()) {
            testingPoint.setPointCode(request.getPointCode());
            return saveTestingPoint(testingPoint, request);
        } else {
            return generateNextPointCode(request.getPointType())
                .flatMap(pointCode -> {
                    testingPoint.setPointCode(pointCode);
                    return saveTestingPoint(testingPoint, request);
                });
        }
    }

    @Override
    public Mono<TestingPoint> update(String id, TestingPoint point) {
        return testingPointRepository.findById(id)
            .switchIfEmpty(Mono.error(CustomException.notFound("TestingPoint", id)))
            .flatMap(existing -> {
                existing.setOrganizationId(point.getOrganizationId());
                existing.setPointCode(point.getPointCode());
                existing.setPointName(point.getPointName());
                existing.setPointType(point.getPointType());
                existing.setZoneId(point.getZoneId());
                existing.setLocationDescription(point.getLocationDescription());
                existing.setStreet(point.getStreet());
                if (point.getCoordinates() != null) {
                    existing.setCoordinates(new TestingPoint.Coordinates(
                        point.getCoordinates().getLatitude(), 
                        point.getCoordinates().getLongitude()
                    ));
                }
                existing.setStatus(point.getStatus());
                existing.setUpdatedAt(LocalDateTime.now());
                return testingPointRepository.save(existing);
            });
    }

    @Override
    public Mono<Void> delete(String id) {
        return testingPointRepository.deleteById(id);
    }

    @Override
    public Mono<TestingPointEnrichedResponse> activate(String id) {
        return testingPointRepository.findById(id)
            .switchIfEmpty(Mono.error(CustomException.notFound("TestingPoint", id)))
            .flatMap(point -> {
                point.setStatus("ACTIVE");
                return testingPointRepository.save(point);
            })
            .flatMap(this::enrichTestingPoint);
    }

    @Override
    public Mono<TestingPointEnrichedResponse> deactivate(String id) {
        return testingPointRepository.findById(id)
            .switchIfEmpty(Mono.error(CustomException.notFound("TestingPoint", id)))
            .flatMap(point -> {
                point.setStatus("INACTIVE");
                return testingPointRepository.save(point);
            })
            .flatMap(this::enrichTestingPoint);
    }

    @Override
    public Flux<TestingPointEnrichedResponse> getAllByOrganization(String organizationId) {
        return testingPointRepository.findByOrganizationId(organizationId)
            .flatMap(this::enrichTestingPoint);
    }

    @Override
    public Flux<TestingPointEnrichedResponse> getAllActiveByOrganization(String organizationId) {
        return testingPointRepository.findByOrganizationIdAndStatus(organizationId, "ACTIVE")
            .flatMap(this::enrichTestingPoint);
    }

    @Override
    public Flux<TestingPointEnrichedResponse> getAllInactiveByOrganization(String organizationId) {
        return testingPointRepository.findByOrganizationIdAndStatus(organizationId, "INACTIVE")
            .flatMap(this::enrichTestingPoint);
    }

    @Override
    public Mono<TestingPointEnrichedResponse> getByIdAndOrganization(String id, String organizationId) {
        return testingPointRepository.findById(id)
            .filter(point -> point.getOrganizationId().equals(organizationId))
            .flatMap(this::enrichTestingPoint)
            .switchIfEmpty(Mono.error(CustomException.notFound("TestingPoint", id)));
    }

    private Mono<String> getCurrentUserOrganizationId() {
        return Mono.just("6896b2ecf3e398570ffd99d3");
    }

    private Mono<TestingPointEnrichedResponse> enrichTestingPoint(TestingPoint point) {
        Mono<ExternalOrganization> orgMono = externalServiceClient
            .getOrganizationById(point.getOrganizationId());

        return orgMono.map(org -> TestingPointEnrichedResponse.builder()
                .id(point.getId())
                .pointCode(point.getPointCode())
                .pointName(point.getPointName())
                .pointType(point.getPointType())
                .zoneId(point.getZoneId())
                .locationDescription(point.getLocationDescription())
                .street(point.getStreet())
                .coordinates(point.getCoordinates())
                .status(point.getStatus())
                .createdAt(point.getCreatedAt())
                .updatedAt(point.getUpdatedAt())
                .organizationId(org)
                .build())
            .switchIfEmpty(Mono.just(TestingPointEnrichedResponse.builder()
                .id(point.getId())
                .pointCode(point.getPointCode())
                .pointName(point.getPointName())
                .pointType(point.getPointType())
                .zoneId(point.getZoneId())
                .locationDescription(point.getLocationDescription())
                .street(point.getStreet())
                .coordinates(point.getCoordinates())
                .status(point.getStatus())
                .createdAt(point.getCreatedAt())
                .updatedAt(point.getUpdatedAt())
                .organizationId(null)
                .build()));
    }

    private Mono<String> generateNextPointCode(String pointType) {
        String prefix = getPointCodePrefix(pointType);

        return testingPointRepository.findAll()
            .filter(tp -> tp.getPointCode() != null && tp.getPointCode().startsWith(prefix))
            .sort((tp1, tp2) -> tp2.getPointCode().compareTo(tp1.getPointCode()))
            .next()
            .map(last -> {
                try {
                    String numberPart = last.getPointCode().substring(2);
                    int number = Integer.parseInt(numberPart);
                    return String.format("%s%03d", prefix, number + 1);
                } catch (Exception e) {
                    return String.format("%s%03d", prefix, 1);
                }
            })
            .defaultIfEmpty(String.format("%s%03d", prefix, 1));
    }
    
    private String getPointCodePrefix(String pointType) {
        if (pointType != null) {
            switch (pointType.toUpperCase()) {
                case "RESERVORIO": return "PR";
                case "RED_DISTRIBUCION": return "PD";
                case "DOMICILIO": return "PM";
                default: return "PT";
            }
        }
        return "PT";
    }

    private Mono<TestingPointResponse> saveTestingPoint(TestingPoint testingPoint, TestingPointCreateRequest request) {
        testingPoint.setPointName(request.getPointName());
        testingPoint.setPointType(request.getPointType());
        testingPoint.setZoneId(request.getZoneId());
        testingPoint.setLocationDescription(request.getLocationDescription());
        testingPoint.setStreet(request.getStreet());
        testingPoint.setCoordinates(new TestingPoint.Coordinates(
            request.getCoordinates().getLatitude(), 
            request.getCoordinates().getLongitude()
        ));
        testingPoint.setCreatedAt(LocalDateTime.now());
        testingPoint.setUpdatedAt(LocalDateTime.now());
        testingPoint.setStatus("ACTIVE");

        return testingPointRepository.save(testingPoint).map(saved -> {
            TestingPointResponse response = new TestingPointResponse();
            response.setId(saved.getId());
            response.setOrganizationId(saved.getOrganizationId());
            response.setPointCode(saved.getPointCode());
            response.setPointName(saved.getPointName());
            response.setPointType(saved.getPointType());
            response.setZoneId(saved.getZoneId());
            response.setLocationDescription(saved.getLocationDescription());
            response.setStreet(saved.getStreet());
            if (saved.getCoordinates() != null) {
                response.setCoordinates(new pe.edu.vallegrande.ms_water_quality.infrastructure.dto.response.TestingPointResponse.Coordinates(
                    saved.getCoordinates().getLatitude(), 
                    saved.getCoordinates().getLongitude()
                ));
            }
            response.setStatus(saved.getStatus());
            response.setCreatedAt(saved.getCreatedAt());
            return response;
        });
    }
}