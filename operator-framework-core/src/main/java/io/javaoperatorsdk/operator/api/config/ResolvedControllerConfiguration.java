package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigurationProvider;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.retry.Retry;

@SuppressWarnings("rawtypes")
public class ResolvedControllerConfiguration<P extends HasMetadata>
    extends DefaultResourceConfiguration<P>
    implements io.javaoperatorsdk.operator.api.config.ControllerConfiguration<P>,
    DependentResourceConfigurationProvider {

  private final String name;
  private final boolean generationAware;
  private final String associatedReconcilerClassName;
  private final Retry retry;
  private final RateLimiter rateLimiter;
  private final Duration maxReconciliationInterval;
  private final String finalizer;
  private final Map<DependentResourceSpec, Object> configurations;
  private final ItemStore<P> itemStore;
  private final ConfigurationService configurationService;
  private final String fieldManager;

  private ResourceEventFilter<P> eventFilter;
  private List<DependentResourceSpec> dependentResources;

  public ResolvedControllerConfiguration(Class<P> resourceClass, ControllerConfiguration<P> other) {
    this(resourceClass, other.getName(), other.isGenerationAware(),
        other.getAssociatedReconcilerClassName(), other.getRetry(), other.getRateLimiter(),
        other.maxReconciliationInterval().orElse(null),
        other.onAddFilter().orElse(null), other.onUpdateFilter().orElse(null),
        other.genericFilter().orElse(null),
        other.getDependentResources(), other.getNamespaces(),
        other.getFinalizerName(), other.getLabelSelector(), Collections.emptyMap(),
        other.getItemStore().orElse(null), other.fieldManager(),
        other.getConfigurationService());
  }

  public static Duration getMaxReconciliationInterval(long interval, TimeUnit timeUnit) {
    return interval > 0 ? Duration.of(interval, timeUnit.toChronoUnit()) : null;
  }

  public static String getAssociatedReconcilerClassName(
      Class<? extends Reconciler> reconcilerClass) {
    return reconcilerClass.getCanonicalName();
  }

  protected Retry ensureRetry(Retry given) {
    return given == null ? ControllerConfiguration.super.getRetry() : given;
  }

  protected RateLimiter ensureRateLimiter(RateLimiter given) {
    return given == null ? ControllerConfiguration.super.getRateLimiter() : given;
  }

  public ResolvedControllerConfiguration(Class<P> resourceClass, String name,
      boolean generationAware, String associatedReconcilerClassName, Retry retry,
      RateLimiter rateLimiter, Duration maxReconciliationInterval,
      OnAddFilter<? super P> onAddFilter, OnUpdateFilter<? super P> onUpdateFilter,
      GenericFilter<? super P> genericFilter,
      List<DependentResourceSpec> dependentResources,
      Set<String> namespaces, String finalizer, String labelSelector,
      Map<DependentResourceSpec, Object> configurations, ItemStore<P> itemStore,
      String fieldManager,
      ConfigurationService configurationService) {
    this(resourceClass, name, generationAware, associatedReconcilerClassName, retry, rateLimiter,
        maxReconciliationInterval, onAddFilter, onUpdateFilter, genericFilter,
        namespaces, finalizer, labelSelector, configurations, itemStore, fieldManager,
        configurationService);
    setDependentResources(dependentResources);
  }

  protected ResolvedControllerConfiguration(Class<P> resourceClass, String name,
      boolean generationAware, String associatedReconcilerClassName, Retry retry,
      RateLimiter rateLimiter, Duration maxReconciliationInterval,
      OnAddFilter<? super P> onAddFilter, OnUpdateFilter<? super P> onUpdateFilter,
      GenericFilter<? super P> genericFilter,
      Set<String> namespaces, String finalizer, String labelSelector,
      Map<DependentResourceSpec, Object> configurations, ItemStore<P> itemStore,
      String fieldManager,
      ConfigurationService configurationService) {
    super(resourceClass, namespaces, labelSelector, onAddFilter, onUpdateFilter, genericFilter,
        itemStore);
    this.configurationService = configurationService;
    this.name = ControllerConfiguration.ensureValidName(name, associatedReconcilerClassName);
    this.generationAware = generationAware;
    this.associatedReconcilerClassName = associatedReconcilerClassName;
    this.retry = ensureRetry(retry);
    this.rateLimiter = ensureRateLimiter(rateLimiter);
    this.maxReconciliationInterval = maxReconciliationInterval;
    this.configurations = configurations != null ? configurations : Collections.emptyMap();
    this.itemStore = itemStore;
    this.finalizer =
        ControllerConfiguration.ensureValidFinalizerName(finalizer, getResourceTypeName());
    this.fieldManager = fieldManager;
  }

  protected ResolvedControllerConfiguration(Class<P> resourceClass, String name,
      Class<? extends Reconciler> reconcilerClas, ConfigurationService configurationService) {
    this(resourceClass, name, false, getAssociatedReconcilerClassName(reconcilerClas), null, null,
        null, null, null, null, null,
        null, null, null, null, null, configurationService);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getFinalizerName() {
    return finalizer;
  }

  @Override
  public boolean isGenerationAware() {
    return generationAware;
  }

  @Override
  public String getAssociatedReconcilerClassName() {
    return associatedReconcilerClassName;
  }

  @Override
  public Retry getRetry() {
    return retry;
  }

  @Override
  public RateLimiter getRateLimiter() {
    return rateLimiter;
  }

  @Override
  public List<DependentResourceSpec> getDependentResources() {
    return dependentResources;
  }

  protected void setDependentResources(List<DependentResourceSpec> dependentResources) {
    this.dependentResources = dependentResources == null ? Collections.emptyList()
        : Collections.unmodifiableList(dependentResources);
  }

  @Override
  public Optional<Duration> maxReconciliationInterval() {
    return Optional.ofNullable(maxReconciliationInterval);
  }

  @Override
  public ConfigurationService getConfigurationService() {
    return configurationService;
  }

  @Override
  public ResourceEventFilter<P> getEventFilter() {
    return eventFilter;
  }

  /**
   * @deprecated Use {@link OnAddFilter}, {@link OnUpdateFilter} and {@link GenericFilter} instead
   *
   * @param eventFilter generic event filter
   */
  @Deprecated(forRemoval = true)
  protected void setEventFilter(ResourceEventFilter<P> eventFilter) {
    this.eventFilter = eventFilter;
  }

  @Override
  public Object getConfigurationFor(DependentResourceSpec spec) {
    return configurations.get(spec);
  }

  @Override
  public Optional<ItemStore<P>> getItemStore() {
    return Optional.ofNullable(itemStore);
  }

  @Override
  public String fieldManager() {
    return fieldManager;
  }
}
