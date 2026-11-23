use futures::{StreamExt, TryStreamExt, stream};
use std::future::Future;

/// Run `job` for every item with at most `limit` in flight. Stops at the first error and returns
/// it; jobs still in flight are cancelled (dropped), which is safe for [`crate::Net::download`].
pub async fn run_bounded<T, E, F, Fut>(items: impl IntoIterator<Item = T>, limit: usize, job: F) -> Result<(), E>
where
    F: FnMut(T) -> Fut,
    Fut: Future<Output = Result<(), E>>,
{
    stream::iter(items)
        .map(job)
        .buffer_unordered(limit.max(1))
        .try_collect::<()>()
        .await
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;
    use std::sync::atomic::{AtomicUsize, Ordering};

    #[tokio::test]
    async fn never_exceeds_the_limit() {
        let running = Arc::new(AtomicUsize::new(0));
        let peak = Arc::new(AtomicUsize::new(0));
        run_bounded(0..50, 4, |_| {
            let (running, peak) = (running.clone(), peak.clone());
            async move {
                let now = running.fetch_add(1, Ordering::SeqCst) + 1;
                peak.fetch_max(now, Ordering::SeqCst);
                tokio::task::yield_now().await;
                running.fetch_sub(1, Ordering::SeqCst);
                Ok::<(), ()>(())
            }
        })
        .await
        .unwrap();
        assert!(peak.load(Ordering::SeqCst) <= 4);
    }

    #[tokio::test]
    async fn returns_the_first_error() {
        let result = run_bounded(0..10, 2, |i| async move { if i == 3 { Err(i) } else { Ok(()) } }).await;
        assert_eq!(result, Err(3));
    }
}
