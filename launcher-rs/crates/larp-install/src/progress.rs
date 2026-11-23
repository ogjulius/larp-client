//! Install progress reporting.

use std::sync::Mutex;

/// Receives install progress from any thread.
pub trait Progress: Send + Sync {
    /// `fraction` in 0..=1, or `None` while indeterminate.
    fn update(&self, status: &str, fraction: Option<f64>);
}

impl<F: Fn(&str, Option<f64>) + Send + Sync> Progress for F {
    fn update(&self, status: &str, fraction: Option<f64>) {
        self(status, fraction);
    }
}

/// Keeps the fraction from going backwards within one status: parallel workers count atomically but
/// can deliver their updates out of order.
#[derive(Debug)]
pub struct Monotonic<P> {
    target: P,
    last: Mutex<Option<(String, f64)>>,
}

impl<P: Progress> Monotonic<P> {
    pub const fn new(target: P) -> Self {
        Self {
            target,
            last: Mutex::new(None),
        }
    }
}

impl<P: Progress> Progress for Monotonic<P> {
    fn update(&self, status: &str, fraction: Option<f64>) {
        // A poisoned lock only means another reporter panicked; progress output is still fine.
        let mut last = self.last.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
        if let (Some(f), Some((s, prev))) = (fraction, last.as_ref())
            && s == status
            && f < *prev
        {
            return;
        }
        *last = fraction.map(|f| (status.to_owned(), f));
        drop(last);
        self.target.update(status, fraction);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn drops_stale_fractions_within_a_step() {
        let seen = Mutex::new(Vec::new());
        let p = Monotonic::new(|s: &str, f: Option<f64>| seen.lock().unwrap().push(format!("{s} {f:?}")));
        p.update("libs", Some(0.2));
        p.update("libs", Some(0.4));
        p.update("libs", Some(0.3));
        p.update("libs", Some(1.0));
        p.update("assets", Some(0.1));
        p.update("assets", None);
        drop(p);
        assert_eq!(
            seen.into_inner().unwrap(),
            [
                "libs Some(0.2)",
                "libs Some(0.4)",
                "libs Some(1.0)",
                "assets Some(0.1)",
                "assets None"
            ]
        );
    }
}
